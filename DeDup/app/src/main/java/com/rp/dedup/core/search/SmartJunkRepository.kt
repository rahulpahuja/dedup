package com.rp.dedup.core.search

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rp.dedup.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.math.pow
import kotlin.math.sqrt

class SmartJunkRepository(private val context: Context) : java.io.Closeable {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.6f)
            .build()
    )

    enum class JunkCategory(
        @StringRes val nameRes: Int,
        @StringRes val descRes: Int,
        val icon: ImageVector,
        val color: Color
    ) {
        SCREENSHOTS(R.string.junk_category_screenshots, R.string.tut_search_title, Icons.Default.Smartphone, Color(0xFF4285F4)),
        MEMES(R.string.junk_category_memes, R.string.ai_image_cleanup_desc, Icons.Default.Mood, Color(0xFFEA4335)),
        DOCUMENTS(R.string.junk_category_documents, R.string.smart_ai_cleanup_desc, Icons.AutoMirrored.Filled.Article, Color(0xFF34A853)),
        BLURRY(R.string.junk_category_blurry, R.string.junk_category_blurry_desc, Icons.Default.PhotoCamera, Color(0xFFFBBC05)),
        POOR_EXPOSURE(R.string.junk_category_poor_exposure, R.string.junk_category_poor_exposure_desc, Icons.Default.BrightnessLow, Color(0xFF9C6FFF))
    }

    data class JunkItem(
        val uri: Uri,
        val category: JunkCategory,
        val labels: List<String>,
        val fileName: String = "",
        val size: Long = 0L,
        val aiReason: String? = null
    )

    /** Scans the most recent [limit] images and groups them into junk categories. */
    suspend fun scanForJunk(
        limit: Int = 1000,
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
        
        var fileName = ""
        var size = 0L

        context.contentResolver.query(
            uri, 
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                fileName = cursor.getString(0) ?: ""
                size = cursor.getLong(1)
            }
        }

        // 1. Classification-based check (Screenshots, Memes, Docs)
        var category = when {
            labels.any { 
                it.contains("screenshot", true) || 
                it.contains("user interface", true) || 
                it.contains("software", true) ||
                it.contains("web page", true)
            } -> JunkCategory.SCREENSHOTS
            
            labels.any { 
                it.contains("meme", true) || 
                it.contains("joke", true) || 
                it.contains("cartoon", true) || 
                it.contains("illustration", true) || 
                it.contains("poster", true) || 
                it.contains("comics", true)
            } -> JunkCategory.MEMES
            
            labels.any { 
                it.contains("text", true) || 
                it.contains("paper", true) || 
                it.contains("document", true) || 
                it.contains("receipt", true) ||
                it.contains("invoice", true) ||
                it.contains("bill", true)
            } -> JunkCategory.DOCUMENTS
            
            else -> null
        }

        var aiReason: String? = null

        // 2. Quality-based check (Blur, Exposure) if not already categorized
        if (category == null) {
            val qualityResult = analyzeQuality(uri)
            if (qualityResult != null) {
                category = qualityResult.first
                aiReason = qualityResult.second
            }
        }

        // 3. Label-based Blur check (ML Kit often flags out of focus)
        if (category == null && labels.any { it.contains("blur", true) || it.contains("out of focus", true) }) {
            category = JunkCategory.BLURRY
            aiReason = "ML Kit detected blur in image labels."
        }

        return category?.let { JunkItem(uri, it, labels, fileName, size, aiReason) }
    }

    private fun analyzeQuality(uri: Uri): Pair<JunkCategory, String>? {
        val bitmap = decodeSampledBitmap(uri, 200, 200) ?: return null
        
        // Exposure Check
        val brightness = calculateAverageBrightness(bitmap)
        if (brightness < 40) return JunkCategory.POOR_EXPOSURE to "Underexposed (Brightness: ${"%.1f".format(brightness)})"
        if (brightness > 225) return JunkCategory.POOR_EXPOSURE to "Overexposed (Brightness: ${"%.1f".format(brightness)})"
        
        // Blur Check (Laplacian Variance)
        val blurScore = calculateLaplacianVariance(bitmap)
        if (blurScore < 100.0) return JunkCategory.BLURRY to "Out of focus (Blur score: ${"%.1f".format(blurScore)})"
        
        return null
    }

    private fun calculateAverageBrightness(bitmap: Bitmap): Double {
        var totalLuma = 0.0
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (pixel in pixels) {
            val r = AndroidColor.red(pixel)
            val g = AndroidColor.green(pixel)
            val b = AndroidColor.blue(pixel)
            totalLuma += (0.299 * r + 0.587 * g + 0.114 * b)
        }
        return totalLuma / (w * h)
    }

    private fun calculateLaplacianVariance(bitmap: Bitmap): Double {
        val w = bitmap.width
        val h = bitmap.height
        val gray = IntArray(w * h)
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = (AndroidColor.red(p) * 0.299 + AndroidColor.green(p) * 0.587 + AndroidColor.blue(p) * 0.114).toInt()
        }
        
        val laplacian = DoubleArray(w * h)
        var mean = 0.0
        
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val center = gray[idx]
                val sum = gray[idx - 1] + gray[idx + 1] + gray[idx - w] + gray[idx + w] - 4 * center
                laplacian[idx] = sum.toDouble()
                mean += sum
            }
        }
        mean /= (w * h)
        
        var variance = 0.0
        for (v in laplacian) {
            variance += (v - mean).pow(2)
        }
        return variance / (w * h)
    }

    private fun decodeSampledBitmap(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, options)
            }
            
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
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
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                uris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(col)))
                count++
            }
        }
        return uris
    }

    override fun close() {
        try { labeler.close() } catch (_: Exception) { }
    }
}
