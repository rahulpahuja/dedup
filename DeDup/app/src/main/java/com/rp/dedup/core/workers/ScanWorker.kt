package com.rp.dedup.core.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rp.dedup.core.repository.ImageScannerRepository
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.db.AppDatabase
import kotlinx.coroutines.flow.toList

class ScanWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val repository = ImageScannerRepository(applicationContext)
        val db = AppDatabase.getDatabase(applicationContext)
        val historyRepo = ScanHistoryRepository(db.scanHistoryDao())

        return try {
            // Simple background scan of images
            // In a real app, this would notify the user if duplicates are found
            repository.scanImagesInParallel().collect { /* just scanning for now */ }
            androidx.work.ListenableWorker.Result.success()
        } catch (e: Exception) {
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}
