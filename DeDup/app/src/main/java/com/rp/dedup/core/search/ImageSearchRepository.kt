package com.rp.dedup.core.search

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class ImageSearchRepository(private val context: Context) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.55f)
            .build()
    )

    // In-memory label cache for the app session: uri → list of label strings
    private val labelCache = ConcurrentHashMap<String, List<String>>()

    data class SearchResult(
        val uri: Uri,
        val matchedLabels: List<String>
    )

    /** Loads up to [limit] most-recent images from MediaStore. */
    fun loadRecentImages(limit: Int = 150): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $limit"
        )?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                uris.add(
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(col)
                    )
                )
            }
        }
        return uris
    }

    /** Runs ML Kit image labeling on a single URI. Results are cached. */
    private suspend fun labelImage(uri: Uri): List<String> {
        labelCache[uri.toString()]?.let { return it }
        return suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        val result = labels.map { it.text }
                        labelCache[uri.toString()] = result
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(emptyList())
                    }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(emptyList())
            }
        }
    }

    /**
     * Labels all [images] concurrently (up to [concurrency] at a time) and
     * returns those whose labels match at least one [queryTokens] term.
     * Calls [onProgress] after each batch.
     */
    suspend fun search(
        query: String,
        onProgress: (labeled: Int, total: Int) -> Unit
    ): List<SearchResult> = coroutineScope {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return@coroutineScope emptyList()

        val images = loadRecentImages()
        val results = mutableListOf<SearchResult>()
        var labeled = 0

        // Process in batches of 8 to avoid hitting ML Kit concurrency limits
        images.chunked(8).forEach { batch ->
            batch.map { uri ->
                async { labelImage(uri) to uri }
            }.awaitAll().forEach { (labels, uri) ->
                labeled++
                val matched = labels.filter { label ->
                    tokens.any { token -> label.lowercase().contains(token) }
                }
                if (matched.isNotEmpty()) results.add(SearchResult(uri, matched))
            }
            onProgress(labeled, images.size)
        }
        results
    }

    private fun tokenize(query: String): List<String> {
        val stopwords = setOf(
            "a", "an", "the", "my", "i", "me", "find", "show", "get",
            "with", "in", "on", "at", "of", "is", "are", "was", "were",
            "and", "or", "for", "to", "image", "photo", "picture", "pic"
        )
        val tokens = query.lowercase()
            .split(" ", ",", ".", ";", "'")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in stopwords }
        
        // Limit to 10 tokens to prevent excessive processing or potential library limits
        return tokens.take(10)
    }
}
