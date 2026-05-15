package com.rp.dedup.core.search

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
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

    companion object {
        private const val TAG = "ImageSearchRepo"
    }

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.45f) // Lowered slightly to improve recall for semantic matches
            .build()
    )

    // Semantic map for "Human" queries to ML Kit labels
    private val semanticMap = mapOf(
        "pet" to listOf("dog", "cat", "animal", "canine", "feline", "bird", "hamster", "rabbit", "paw"),
        "food" to listOf("dish", "meal", "cuisine", "vegetable", "fruit", "drink", "tableware", "pizza", "burger", "cake", "bread", "cooking"),
        "nature" to listOf("tree", "mountain", "sky", "grass", "water", "lake", "ocean", "forest", "flower", "sunset", "sunrise", "beach", "desert"),
        "document" to listOf("text", "paper", "font", "receipt", "whiteboard", "document", "identity document", "handwriting"),
        "vehicle" to listOf("car", "bus", "truck", "motorcycle", "bicycle", "aircraft", "boat", "train", "wheel", "automotive"),
        "portrait" to listOf("person", "face", "smile", "selfie", "human", "man", "woman", "child", "boy", "girl"),
        "interior" to listOf("room", "furniture", "table", "chair", "wall", "building", "home", "bedroom", "kitchen", "living room"),
        "landscape" to listOf("view", "horizon", "scenery", "outdoor", "valley", "cliff", "hill"),
        "sports" to listOf("ball", "player", "game", "stadium", "jersey", "running", "soccer", "basketball", "tennis", "gym"),
        "art" to listOf("painting", "drawing", "illustration", "sculpture", "mural", "sketch", "sketching", "graphic design")
    )

    // In-memory label cache for the app session: uri → list of label strings
    private val labelCache = ConcurrentHashMap<String, List<String>>()

    data class SearchResult(
        val uri: Uri,
        val matchedLabels: List<String>
    )

    /** Loads up to [limit] most-recent images from MediaStore. */
    fun loadRecentImages(limit: Int = 200): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                uris.add(
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(col)
                    )
                )
                count++
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
                        val result = labels.map { it.text.lowercase() }
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
        Log.d(TAG, "Starting search for query: '$query'")
        val tokens = tokenize(query)
        if (tokens.isEmpty()) {
            Log.d(TAG, "No valid tokens found in query. Aborting search.")
            return@coroutineScope emptyList()
        }
        Log.d(TAG, "Tokenized query: $tokens")

        val images = loadRecentImages()
        Log.d(TAG, "Loaded ${images.size} recent images for scanning")
        
        val results = mutableListOf<SearchResult>()
        var labeledCount = 0

        // Process in batches of 8 to avoid hitting ML Kit concurrency limits
        images.chunked(8).forEach { batch ->
            batch.map { uri ->
                async { labelImage(uri) to uri }
            }.awaitAll().forEach { (labels, uri) ->
                labeledCount++
                
                val matched = labels.filter { label ->
                    val lowerLabel = label.lowercase()
                    tokens.any { token ->
                        // 1. Exact match with label
                        val isExactMatch = lowerLabel == token
                        // 2. Exact semantic match (e.g. user search "pet", label is "dog")
                        val isSemanticMatch = semanticMap[token]?.contains(lowerLabel) == true
                        // 3. Partial match (e.g. user search "bike", label is "bicycle")
                        val isPartialMatch = lowerLabel.contains(token)
                        // 4. Reverse semantic match (e.g. user search "dog", token is in "pet" list)
                        val isReverseSemanticMatch = semanticMap.entries.any { 
                            it.key == lowerLabel && it.value.contains(token) 
                        }

                        if (isExactMatch || isSemanticMatch || isPartialMatch || isReverseSemanticMatch) {
                            Log.v(TAG, "Match found! Uri: $uri | Token: '$token' | Label: '$lowerLabel' " +
                                "(Exact: $isExactMatch, Sem: $isSemanticMatch, Part: $isPartialMatch, Rev: $isReverseSemanticMatch)")
                            true
                        } else {
                            false
                        }
                    }
                }
                if (matched.isNotEmpty()) {
                    results.add(SearchResult(uri, matched))
                }
            }
            onProgress(labeledCount, images.size)
        }
        
        Log.d(TAG, "Search finished. Found ${results.size} matches out of $labeledCount images.")
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
