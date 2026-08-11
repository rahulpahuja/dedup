package com.rp.dedup.core.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.rp.dedup.MainActivity
import com.rp.dedup.UIConstants
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.notifications.AppNotificationManager
import com.rp.dedup.core.repository.SemanticDuplicateRepository
import kotlin.math.roundToInt

/**
 * Periodic background worker that scans the semantic image index for duplicates and,
 * when meaningful reclaimable space is found, notifies the user. Scheduling lives in
 * [enqueuePeriodic].
 */
class ScanWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        val dao = AppDatabase.getDatabase(applicationContext).imageEmbeddingDao()
        val repository = SemanticDuplicateRepository(dao)
        val groups = repository.findDuplicateGroups()
        val reclaimableBytes = computeReclaimableBytes(groups) { uri -> querySize(applicationContext, uri) }

        Log.d(TAG, "Scan complete: ${groups.size} duplicate group(s), $reclaimableBytes reclaimable byte(s)")

        if (shouldNotify(reclaimableBytes)) {
            notifyDuplicatesFound(applicationContext, reclaimableBytes)
        }

        Result.success(
            Data.Builder()
                .putInt(KEY_GROUP_COUNT, groups.size)
                .putLong(KEY_RECLAIMABLE_BYTES, reclaimableBytes)
                .build()
        )
    } catch (t: Throwable) {
        Log.e(TAG, "Background scan failed", t)
        Result.retry()
    }

    private fun notifyDuplicatesFound(context: Context, reclaimableBytes: Long) {
        val notificationManager = AppNotificationManager(context)
        if (!notificationManager.hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("target_route", UIConstants.ROUTE_DASHBOARD)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        notificationManager.showSimpleNotification(
            id = NOTIFICATION_ID,
            title = "Duplicates found",
            message = "You could free up ${formatBytes(reclaimableBytes)} by cleaning up duplicate photos.",
            contentIntent = contentIntent
        )
    }

    companion object {
        private const val TAG = "ScanWorker"
        private const val NOTIFICATION_ID = 2001
        private const val RECLAIMABLE_THRESHOLD_BYTES = 50L * 1024 * 1024 // 50 MB
        const val KEY_GROUP_COUNT = "group_count"
        const val KEY_RECLAIMABLE_BYTES = "reclaimable_bytes"

        /**
         * Sums the size of every file but the first ("keeper") in each duplicate group.
         * Pure/testable: size lookup is injected rather than done via ContentResolver directly.
         */
        internal fun computeReclaimableBytes(groups: List<List<Uri>>, sizeOf: (Uri) -> Long): Long =
            groups.sumOf { group -> group.drop(1).sumOf(sizeOf) }

        /** Only worth interrupting the user once there's a meaningful amount of space to free. */
        internal fun shouldNotify(reclaimableBytes: Long): Boolean =
            reclaimableBytes >= RECLAIMABLE_THRESHOLD_BYTES

        internal fun formatBytes(bytes: Long): String = when {
            bytes >= 1024L * 1024 * 1024 -> "${(bytes / (1024.0 * 1024 * 1024) * 10).roundToInt() / 10.0} GB"
            bytes >= 1024L * 1024 -> "${(bytes / (1024.0 * 1024) * 10).roundToInt() / 10.0} MB"
            bytes >= 1024L -> "${(bytes / 1024.0 * 10).roundToInt() / 10.0} KB"
            else -> "$bytes B"
        }

        private fun querySize(context: Context, uri: Uri): Long {
            val projection = arrayOf(MediaStore.MediaColumns.SIZE)
            return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                } else {
                    0L
                }
            } ?: 0L
        }
    }
}
