package com.rp.dedup.core.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rp.dedup.MainActivity
import com.rp.dedup.UIConstants
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.model.ForecastConfidence
import com.rp.dedup.core.model.StorageForecast
import com.rp.dedup.core.notifications.AppNotificationManager
import com.rp.dedup.core.repository.SemanticDuplicateRepository
import com.rp.dedup.core.repository.StorageForecastingRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Periodic background worker that scans the semantic image index for duplicates and,
 * when meaningful reclaimable space is found, notifies the user. Scheduled via
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

        val forecastingRepository = StorageForecastingRepository(
            AppDatabase.getDatabase(applicationContext).storageTrendDao()
        )
        forecastingRepository.recordSnapshotIfNecessary(freeBytes())
        val forecast = forecastingRepository.forecast.first()
        val lowStorageWarning = isLowStorage(forecast)
        Log.d(TAG, "Storage forecast: $forecast, low-storage warning: $lowStorageWarning")

        if (forecast != null) {
            val dataStoreManager = DataStoreManager(applicationContext)
            val lastNotifiedDay = dataStoreManager.readData(DataStoreManager.LAST_LOW_STORAGE_NOTIFICATION_DAY, "").first()
            val today = todayKey()
            if (shouldNotifyLowStorage(lowStorageWarning, lastNotifiedDay.ifEmpty { null }, today)) {
                notifyLowStorage(applicationContext, forecast)
                dataStoreManager.writeData(DataStoreManager.LAST_LOW_STORAGE_NOTIFICATION_DAY, today)
            }
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

    private fun notifyLowStorage(context: Context, forecast: StorageForecast) {
        val notificationManager = AppNotificationManager(context)
        if (!notificationManager.hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("target_route", UIConstants.ROUTE_DASHBOARD)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            LOW_STORAGE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dayWord = if (forecast.daysRemaining == 1) "day" else "days"
        notificationManager.showSimpleNotification(
            id = LOW_STORAGE_NOTIFICATION_ID,
            title = "Storage running low",
            message = "You'll run out of storage in about ${forecast.daysRemaining} $dayWord — " +
                "clean up now to avoid running out.",
            isUrgent = true,
            contentIntent = contentIntent
        )
    }

    private fun freeBytes(): Long {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    companion object {
        private const val TAG = "ScanWorker"
        private const val WORK_NAME = "periodic_duplicate_scan"
        private const val NOTIFICATION_ID = 2001
        private const val LOW_STORAGE_NOTIFICATION_ID = 2002
        private const val RECLAIMABLE_THRESHOLD_BYTES = 50L * 1024 * 1024 // 50 MB
        private const val LOW_STORAGE_THRESHOLD_DAYS = 5
        const val KEY_GROUP_COUNT = "group_count"
        const val KEY_RECLAIMABLE_BYTES = "reclaimable_bytes"

        /** "yyyy-MM-dd" for the given instant — used as a once-per-day dedupe key. */
        internal fun todayKey(now: Long = System.currentTimeMillis()): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

        /**
         * Pure/testable: fire the low-storage notification only when the condition holds
         * AND we haven't already notified today, so re-running the worker several times
         * in one day doesn't spam the user.
         */
        internal fun shouldNotifyLowStorage(
            isLowStorage: Boolean,
            lastNotifiedDay: String?,
            today: String,
        ): Boolean = isLowStorage && lastNotifiedDay != today

        /**
         * Schedules a daily duplicate scan. Battery-friendly: runs only when the battery
         * isn't low. KEEP policy: re-calling this (e.g. on every app start) is a no-op if
         * the periodic work is already scheduled.
         */
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<ScanWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Cancels the periodic scan (e.g. when the user disables background scanning). */
        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Sums the size of every file but the first ("keeper") in each duplicate group.
         * Pure/testable: size lookup is injected rather than done via ContentResolver directly.
         */
        internal fun computeReclaimableBytes(groups: List<List<Uri>>, sizeOf: (Uri) -> Long): Long =
            groups.sumOf { group -> group.drop(1).sumOf(sizeOf) }

        /** Only worth interrupting the user once there's a meaningful amount of space to free. */
        internal fun shouldNotify(reclaimableBytes: Long): Boolean =
            reclaimableBytes >= RECLAIMABLE_THRESHOLD_BYTES

        /**
         * Pure/testable: true once the forecast is confident enough and storage is
         * running out soon enough to be worth warning the user about. A LOW-confidence
         * forecast (fewer than 3 snapshots) is excluded to avoid false alarms.
         */
        internal fun isLowStorage(
            forecast: StorageForecast?,
            thresholdDays: Int = LOW_STORAGE_THRESHOLD_DAYS,
        ): Boolean =
            forecast != null &&
                forecast.confidence != ForecastConfidence.LOW &&
                forecast.daysRemaining <= thresholdDays

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
