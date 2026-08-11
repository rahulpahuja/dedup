package com.rp.dedup.core.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** A group of previously-indexed photos taken on this day, [yearsAgo] years ago. */
data class MemoryGroup(
    val yearsAgo: Int,
    val uris: List<Uri>,
)

/**
 * Groups previously-indexed photo URIs (from [com.rp.dedup.core.dao.ImageEmbeddingDao])
 * into "on this day, N years ago" buckets by cross-referencing MediaStore's DATE_TAKEN
 * at read time. No DB schema change: the embedding index only stores when a photo was
 * indexed, not when it was taken.
 */
class MemoriesRepository(private val context: Context) {

    /**
     * Groups [uris] whose capture date shares [now]'s month and day but falls in an
     * earlier year. URIs with no readable DATE_TAKEN are skipped rather than crashing.
     * Groups are sorted by [MemoryGroup.yearsAgo] ascending.
     */
    suspend fun findMemories(
        uris: List<Uri>,
        now: Calendar = Calendar.getInstance(),
    ): List<MemoryGroup> = withContext(Dispatchers.IO) {
        val targetMonth = now.get(Calendar.MONTH)
        val targetDay = now.get(Calendar.DAY_OF_MONTH)
        val currentYear = now.get(Calendar.YEAR)

        uris.mapNotNull { uri -> dateTaken(uri)?.let { uri to it } }
            .filter { (_, takenAt) -> isOnThisDayInAnEarlierYear(takenAt, targetMonth, targetDay, currentYear) }
            .groupBy { (_, takenAt) -> currentYear - yearOf(takenAt) }
            .map { (yearsAgo, entries) -> MemoryGroup(yearsAgo, entries.map { it.first }) }
            .sortedBy { it.yearsAgo }
    }

    private fun isOnThisDayInAnEarlierYear(
        takenAt: Long,
        targetMonth: Int,
        targetDay: Int,
        currentYear: Int,
    ): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = takenAt }
        return cal.get(Calendar.MONTH) == targetMonth &&
            cal.get(Calendar.DAY_OF_MONTH) == targetDay &&
            cal.get(Calendar.YEAR) < currentYear
    }

    private fun yearOf(epochMs: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.YEAR)

    private fun dateTaken(uri: Uri): Long? {
        val projection = arrayOf(MediaStore.Images.Media.DATE_TAKEN)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }
}
