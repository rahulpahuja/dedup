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
import kotlin.coroutines.resume

class SmartJunkRepository(private val context: Context) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.6f)
            .build()
    )

    enum class JunkCategory(val displayName: String, val description: String) {
        SCREENSHOTS("Screenshots", "UI captures and system screens"),
        MEMES("Memes & Graphics", "Internet memes and digital illustrations"),
        DOCUMENTS("Receipts & Docs", "Text-heavy images and documents"),
        BLURRY("Blurry Shots", "Low-quality or out-of-focus images")
    }

    data class JunkItem(
        val uri: Uri,
        val category: JunkCategory,
        val labels: List<String>
    )

    /** Scans the most recent [limit] images and groups them into junk categories. */
    suspend fun scanForJunk(
        limit: Int = 200,
        onProgress: (scanned: Int, total: Int) -> Unit
    ): Map<JunkCategory, List<JunkItem>> = coroutineScope {
        val images = loadRecentImages(limit)
        val junkGroups = mutableMapOf<JunkCategory, MutableList<JunkItem>>()
        var scanned = 0

        images.chunked(10).forEach { batch ->
            batch.map { uri ->
                async { processImage(uri) }
            }.awaitAll().filterNotNull().forEach { junkItem ->
                junkGroups.getOrPut(junkItem.category) { mutableListOf() }.add(junkItem)
            }
            scanned += batch.size
            onProgress(scanned, images.size)
        }

        junkGroups
    }

    private suspend fun processImage(uri: Uri): JunkItem? {
        val labels = getLabels(uri)
        if (labels.isEmpty()) return null

        val category = when {
            // Logic for Screenshots (ML Kit often labels UI elements, text, or screenshots specifically)
            labels.any { it.contains("screenshot", true) || it.contains("font", true) || it.contains("multimedia", true) } -> JunkCategory.SCREENSHOTS
            
            // Logic for Memes (cartoons, illustrations, posters)
            labels.any { it.contains("cartoon", true) || it.contains("illustration", true) || it.contains("poster", true) || it.contains("clip art", true) } -> JunkCategory.MEMES
            
            // Logic for Documents/Receipts
            labels.any { it.contains("text", true) || it.contains("paper", true) || it.contains("document", true) || it.contains("receipt", true) } -> JunkCategory.DOCUMENTS
            
            else -> null
        }

        return category?.let { JunkItem(uri, it, labels) }
    }

    private suspend fun getLabels(uri: Uri): List<String> = suspendCancellableCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    if (cont.isActive) cont.resume(labels.map { it.text })
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(emptyList())
                }
        } catch (e: Exception) {
            Log.e("SmartJunk", "Error labeling $uri", e)
            if (cont.isActive) cont.resume(emptyList())
        }
    }

    private fun loadRecentImages(limit: Int): List<Uri> {
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
                uris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(col)))
            }
        }
        return uris
    }
}
