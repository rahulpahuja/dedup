package com.rp.dedup.core.search

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

class ImageSearchRepository(private val context: Context) {

    companion object {
        private const val TAG = "ImageSearchRepo"
        private const val MAX_RESULTS = 500
    }

    data class SearchResult(
        val uri: Uri,
        val matchedLabels: List<String>
    )

    // Maps user-facing query terms to folder/file name patterns that MediaStore will match.
    // Keys are stemmed lowercase tokens; values are the LIKE patterns (without '%' — added at query time).
    private val semanticExpansions: Map<String, List<String>> = mapOf(
        "screenshot"  to listOf("screenshot"),
        "screen"      to listOf("screenshot"),
        "whatsapp"    to listOf("whatsapp"),
        "telegram"    to listOf("telegram"),
        "instagram"   to listOf("instagram"),
        "facebook"    to listOf("facebook"),
        "twitter"     to listOf("twitter"),
        "snapchat"    to listOf("snapchat"),
        "tiktok"      to listOf("tiktok"),
        "camera"      to listOf("camera", "dcim"),
        "download"    to listOf("download"),
        "document"    to listOf("document", "doc", "scan"),
        "social"      to listOf("instagram", "facebook", "twitter", "snapchat", "tiktok"),
        "edited"      to listOf("snapseed", "lightroom", "vsco", "picsart", "edited"),
        "selfie"      to listOf("selfie", "front"),
        "burst"       to listOf("burst"),
        "received"    to listOf("received", "shared"),
        "backup"      to listOf("backup"),
        "raw"         to listOf(".dng", ".raw", ".arw"),
        "video"       to listOf("video"),
    )

    /**
     * Searches MediaStore images whose DISPLAY_NAME, BUCKET_DISPLAY_NAME, or RELATIVE_PATH
     * contains any of the expanded terms derived from [query].
     * Returns results ordered by most-recently-added.
     */
    suspend fun search(
        query: String,
        onProgress: (labeled: Int, total: Int) -> Unit
    ): List<SearchResult> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) {
            Log.d(TAG, "No valid tokens in query '$query'")
            return emptyList()
        }

        val searchTerms = expandTokens(tokens)
        Log.d(TAG, "Query '$query' → tokens $tokens → terms $searchTerms")

        val results = queryMediaStore(searchTerms)
        onProgress(results.size, results.size)
        Log.d(TAG, "Found ${results.size} results")
        return results
    }

    private fun expandTokens(tokens: List<String>): List<String> {
        return tokens.flatMap { token ->
            val expansions = semanticExpansions[token]
            if (expansions != null) expansions else listOf(token)
        }.distinct()
    }

    private fun queryMediaStore(terms: List<String>): List<SearchResult> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        // Build: (DISPLAY_NAME LIKE ? OR BUCKET_DISPLAY_NAME LIKE ? OR RELATIVE_PATH LIKE ?)
        // for each term, all joined with OR.
        val searchColumns = listOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        val selectionClauses = terms.flatMap { _ -> searchColumns.map { col -> "$col LIKE ?" } }
        val selection = selectionClauses.joinToString(" OR ")
        val selectionArgs = terms.flatMap { term -> List(searchColumns.size) { "%$term%" } }.toTypedArray()

        val results = mutableListOf<SearchResult>()

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            var count = 0
            while (cursor.moveToNext() && count < MAX_RESULTS) {
                val id     = cursor.getLong(idCol)
                val name   = cursor.getString(nameCol).orEmpty()
                val bucket = cursor.getString(bucketCol).orEmpty()

                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                // Show bucket first (most informative), then filename as secondary label.
                val labels = buildList {
                    if (bucket.isNotBlank()) add(bucket)
                    if (name.isNotBlank()) add(name)
                }

                results.add(SearchResult(uri, labels))
                count++
            }
        }

        return results
    }

    private fun tokenize(query: String): List<String> {
        val stopwords = setOf(
            "a", "an", "the", "my", "i", "me", "find", "show", "get",
            "with", "in", "on", "at", "of", "is", "are", "was", "were",
            "and", "or", "for", "to",
            "image", "images", "photo", "photos", "picture", "pictures", "pic", "pics"
        )
        return query.lowercase()
            .split(" ", ",", ".", ";", "'")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in stopwords }
            .map { stem(it) }
            .filter { it.isNotEmpty() && it !in stopwords }
            .distinct()
            .take(10)
    }

    // Strips trailing 's' to handle plurals: "screenshots" → "screenshot", "downloads" → "download".
    // Guard: only when result ≥ 3 chars and word doesn't end in "ss" (e.g. "class" stays "class").
    private fun stem(word: String): String {
        return if (word.length > 3 && word.endsWith('s') && !word.endsWith("ss")) {
            word.dropLast(1)
        } else {
            word
        }
    }
}
