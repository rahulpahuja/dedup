package com.rp.dedup.core.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Placeholder background worker. Scanning requires a live ViewModel/UI context to
 * process results and notify the user — a fire-and-discard collect() provides no value
 * and wastes CPU/battery. This worker is kept as a stub for future notification-based
 * background scanning (e.g., show a notification when new duplicates are found).
 */
class ScanWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = Result.success()
}
