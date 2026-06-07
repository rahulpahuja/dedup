package com.rp.dedup.core.image

import android.content.Context
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
import kotlinx.coroutines.tasks.await

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

    /**
     * Scores every image in every group concurrently, then marks the best shot.
     * All groups are dispatched in parallel on Dispatchers.IO (bitmap loads + ML Kit);
     * within each group, images are also scored in parallel.
     */
    suspend fun analyzeGroups(context: Context, groups: List<List<ScannedImage>>): List<List<ScannedImage>> =
        coroutineScope {
            groups.map { group -> async(Dispatchers.IO) { analyzeGroup(context, group) } }
                .awaitAll()
        }

    suspend fun analyzeGroup(context: Context, group: List<ScannedImage>): List<ScannedImage> {
        if (group.size <= 1) return group

        val scoredImages = coroutineScope {
            group.map { image -> async(Dispatchers.IO) { image.copy(qualityScore = calculateQualityScore(context, image)) } }
                .awaitAll()
        }

        val sorted = scoredImages.sortedByDescending { it.qualityScore }
        return sorted.mapIndexed { index, image -> image.copy(isAiSuggestion = index == 0) }
    }

    private suspend fun calculateQualityScore(context: Context, scannedImage: ScannedImage): Float {
        val uri = scannedImage.uri.toUri()
        val bitmap = ImageScannerRepository.loadBitmapEfficiently(context, uri) ?: return 0f
        
        var score = 0f

        // 1. Basic Sharpness Heuristic: File size vs Resolution
        // Higher entropy (file size) usually means more detail/sharpness for similar content
        score += (scannedImage.sizeInBytes / 1024f) / 100f // 1 point per 100KB

        // 2. Face & Smile Detection
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = faceDetector.process(image).await()
            
            // Add points for each face found
            score += faces.size * 5f
            
            // Add points for smiles
            faces.forEach { face ->
                val smileProb = face.smilingProbability ?: 0f
                score += smileProb * 10f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed for ${scannedImage.uri}", e)
        } finally {
            bitmap.recycle()
        }

        return score
    }
}
