package com.rp.dedup.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.rp.dedup.core.model.ScannedImage
import com.rp.dedup.core.repository.ImageScannerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlin.math.pow

object BestShotAnalyzer {
    private const val TAG = "BestShotAnalyzer"

    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
    }

    // Gates concurrent bitmap loads across all groups and images.
    private val bitmapSlots = Semaphore(4)

    /**
     * Releases the ML Kit FaceDetector's native resources.
     */
    fun close() {
        try { faceDetector.close() } catch (_: Exception) { }
    }

    /**
     * Scores every image in every group concurrently, then marks the best shot.
     */
    suspend fun analyzeGroups(context: Context, groups: List<List<ScannedImage>>): List<List<ScannedImage>> =
        coroutineScope {
            groups.map { group -> async(Dispatchers.IO) { analyzeGroup(context, group) } }
                .awaitAll()
        }

    suspend fun analyzeGroup(context: Context, group: List<ScannedImage>): List<ScannedImage> {
        if (group.size <= 1) return group

        val scoredImages = coroutineScope {
            group.map { image ->
                async(Dispatchers.IO) {
                    image.copy(qualityScore = calculateQualityScore(context, image))
                }
            }.awaitAll()
        }

        val sorted = scoredImages.sortedByDescending { it.qualityScore }
        return sorted.mapIndexed { index, image -> image.copy(isAiSuggestion = index == 0) }
    }

    private suspend fun calculateQualityScore(context: Context, scannedImage: ScannedImage): Float {
        val uri = scannedImage.uri.toUri()
        val bitmap = bitmapSlots.withPermit {
            ImageScannerRepository.loadBitmapEfficiently(context, uri, targetWidth = 400)
        } ?: return 0f

        var score = 0f

        // 1. Sharpness Score (Laplacian Variance) - Max ~50 pts
        val blurScore = calculateLaplacianVariance(bitmap)
        score += (blurScore.toFloat() / 20f).coerceAtMost(50f)

        // 2. Exposure Penalty - Max 10 pts reduction
        val brightness = calculateAverageBrightness(bitmap)
        if (brightness < 45 || brightness > 225) score -= 10f

        // 3. Face & Expression Score - Max ~40 pts
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = faceDetector.process(image).await()
            score += faces.size * 5f // 5 pts per face
            faces.forEach { face ->
                val smileProb = face.smilingProbability ?: 0f
                val eyesOpenProb = (face.leftEyeOpenProbability ?: 0.5f) * (face.rightEyeOpenProbability ?: 0.5f)
                score += smileProb * 15f
                score += eyesOpenProb * 10f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed for ${scannedImage.uri}", e)
        } finally {
            bitmap.recycle()
        }

        // 4. File Size (Tie-breaker/Detail proxy)
        score += (scannedImage.sizeInBytes / 1024f) / 1000f

        return score
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
}
