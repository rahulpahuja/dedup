package com.rp.dedup.core.repository

import android.net.Uri
import android.util.Log
import com.rp.dedup.core.dao.ImageEmbeddingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Groups stored image embeddings into semantically similar clusters.
 *
 * Uses the existing [ImageEmbeddingDao] — no new DB tables needed.
 * Comparisons are done per-bucket (album) to keep the O(n²) work tractable:
 * within a 500-image bucket this is 125k comparisons, well under 1s on device.
 */
class SemanticDuplicateRepository(private val dao: ImageEmbeddingDao) {

    companion object {
        private const val TAG = "SemanticDupeRepo"
        private const val DUPLICATE_THRESHOLD = 0.92f
        private const val MAX_PER_BUCKET = 1000
    }

    /**
     * Returns groups of semantically duplicate image URIs.
     * Each inner list has ≥ 2 URIs that are visually near-identical.
     * [onProgress] receives (bucketsProcessed, totalBuckets).
     */
    suspend fun findDuplicateGroups(
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<List<Uri>> = withContext(Dispatchers.Default) {

        val indexed = dao.count()
        if (indexed == 0) {
            Log.d(TAG, "Embedding index is empty — run an image scan first to build it")
            return@withContext emptyList()
        }

        val entities = dao.getAll()
        Log.d(TAG, "Loaded ${entities.size} embeddings for semantic dedup")

        // Pre-normalize all vectors once
        val normalized = entities.map { entity ->
            val norm = sqrt(entity.embedding.map { it * it }.sum())
            if (norm == 0f) entity.embedding else FloatArray(entity.embedding.size) { entity.embedding[it] / norm }
        }

        // Group indices by bucket so we only compare within the same album
        val bucketGroups = entities.indices.groupBy { entities[it].bucketName }
        val totalBuckets = bucketGroups.size
        var processedBuckets = 0

        // Union-Find
        val parent = IntArray(entities.size) { it }
        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (cur != root) { val next = parent[cur]; parent[cur] = root; cur = next }
            return root
        }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }

        bucketGroups.forEach { (_, indices) ->
            val capped = if (indices.size > MAX_PER_BUCKET) indices.take(MAX_PER_BUCKET) else indices
            for (i in capped.indices) {
                for (j in i + 1 until capped.size) {
                    val iIdx = capped[i]; val jIdx = capped[j]
                    val score = dotProduct(normalized[iIdx], normalized[jIdx])
                    if (score >= DUPLICATE_THRESHOLD) union(iIdx, jIdx)
                }
            }
            processedBuckets++
            onProgress(processedBuckets, totalBuckets)
        }

        // Collect groups with ≥ 2 members
        val rootMap = mutableMapOf<Int, MutableList<Int>>()
        entities.indices.forEach { i -> rootMap.getOrPut(find(i)) { mutableListOf() }.add(i) }

        rootMap.values
            .filter { it.size >= 2 }
            .map { group -> group.map { Uri.parse(entities[it].uri) } }
            .also { Log.d(TAG, "Found ${it.size} semantic duplicate groups") }
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}
