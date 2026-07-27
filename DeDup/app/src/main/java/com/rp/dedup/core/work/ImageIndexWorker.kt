package com.rp.dedup.core.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.search.EmbedderProvider
import com.rp.dedup.core.search.ImageIndexRepository

class ImageIndexWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG       = "ImageIndexWorker"
        private const val WORK_NAME = "image_semantic_index"

        fun enqueue(context: Context) {
            // Only run when the device is idle (screen off, not actively being used).
            // This prevents the TFLite native runtime from being loaded on the main
            // process while the user is interacting with the app, which could cause
            // a visible crash if the model is incompatible.
            val constraints = Constraints.Builder()
                .setRequiresDeviceIdle(true)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ImageIndexWorker>()
                    .setConstraints(constraints)
                    .build()
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting semantic indexing")
        return try {
            val dao      = AppDatabase.getDatabase(applicationContext).imageEmbeddingDao()
            val embedder = EmbedderProvider(applicationContext)

            if (!embedder.isAvailable) {
                Log.w(TAG, "Embedder unavailable — semantic indexing skipped")
                return Result.failure()
            }

            val repo = ImageIndexRepository(applicationContext, dao, embedder)
            repo.indexImages { indexed, total ->
                Log.d(TAG, "Indexed $indexed / $total")
            }

            embedder.close()
            Log.d(TAG, "Semantic indexing complete")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Semantic indexing failed", t)
            Result.failure()
        }
    }
}
