package com.rp.dedup.core.workers

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.SemanticDuplicateRepository

/**
 * Periodic background worker that scans the semantic image index for duplicates and
 * reports how many groups / how much reclaimable space were found. Scheduling lives in
 * [enqueuePeriodic]; this worker only runs the scan and returns the result — it does not
 * itself notify the user (see the caller for notification wiring).
 */
class ScanWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        val dao = AppDatabase.getDatabase(applicationContext).imageEmbeddingDao()
        val repository = SemanticDuplicateRepository(dao)
        val groups = repository.findDuplicateGroups()
        val reclaimableBytes = computeReclaimableBytes(groups) { uri -> querySize(applicationContext, uri) }

        Log.d(TAG, "Scan complete: ${groups.size} duplicate group(s), $reclaimableBytes reclaimable byte(s)")

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

    companion object {
        private const val TAG = "ScanWorker"
        const val KEY_GROUP_COUNT = "group_count"
        const val KEY_RECLAIMABLE_BYTES = "reclaimable_bytes"

        /**
         * Sums the size of every file but the first ("keeper") in each duplicate group.
         * Pure/testable: size lookup is injected rather than done via ContentResolver directly.
         */
        internal fun computeReclaimableBytes(groups: List<List<Uri>>, sizeOf: (Uri) -> Long): Long =
            groups.sumOf { group -> group.drop(1).sumOf(sizeOf) }

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
