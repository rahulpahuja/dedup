package com.rp.dedup.core.search

import android.net.Uri
import android.util.Log
import com.rp.dedup.core.dao.ImageEmbeddingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Semantic image search: embeds the user query with MediaPipe Text Embedder, loads
 * all stored embeddings from Room, scores each with cosine similarity, and returns
 * results above [SIMILARITY_THRESHOLD] ordered by descending score.
 *
 * Falls back to the LIKE-based [ImageSearchRepository] when:
 *   - The semantic index is empty (first launch before indexing completes), or
 *   - The embedder model file is missing from assets.
 */
class SemanticSearchRepository(
    private val dao: ImageEmbeddingDao,
    private val embedder: EmbedderProvider,
    private val fallback: ImageSearchRepository
) {

    companion object {
        private const val TAG              = "SemanticSearchRepo"
        private const val SIMILARITY_THRESHOLD = 0.25f
        private const val MAX_RESULTS      = 200
    }

    /**
     * Returns results as [ImageSearchRepository.SearchResult] so the ViewModel and UI
     * require no changes to their existing types.
     */
    suspend fun search(
        query: String,
        onProgress: (Int, Int) -> Unit
    ): List<ImageSearchRepository.SearchResult> = withContext(Dispatchers.IO) {

        val indexSize = dao.count()
        if (!embedder.isAvailable || indexSize == 0) {
            Log.d(TAG, "Falling back to LIKE search (indexed=$indexSize, embedder=${embedder.isAvailable})")
            return@withContext fallback.search(query, onProgress)
        }

        val queryVec = embedder.embed(query) ?: run {
            Log.w(TAG, "Query embedding failed — falling back to LIKE search")
            return@withContext fallback.search(query, onProgress)
        }

        val allEntities = dao.getAll()
        Log.d(TAG, "Scoring ${allEntities.size} embeddings for query '$query'")
        onProgress(0, allEntities.size)

        val results = allEntities
            .mapIndexed { idx, entity ->
                val score = cosineSimilarity(queryVec, entity.embedding)
                if ((idx + 1) % 100 == 0) onProgress(idx + 1, allEntities.size)
                entity to score
            }
            .filter  { (_, score) -> score >= SIMILARITY_THRESHOLD }
            .sortedByDescending { (_, score) -> score }
            .take(MAX_RESULTS)
            .map { (entity, _) ->
                ImageSearchRepository.SearchResult(
                    uri           = Uri.parse(entity.uri),
                    matchedLabels = buildList {
                        if (entity.bucketName.isNotBlank()) add(entity.bucketName)
                        if (entity.displayName.isNotBlank()) add(entity.displayName)
                    }
                )
            }

        onProgress(allEntities.size, allEntities.size)
        Log.d(TAG, "Semantic search found ${results.size} results")
        results
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot   += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA == 0f || normB == 0f) 0f
        else dot / (sqrt(normA) * sqrt(normB))
    }
}
