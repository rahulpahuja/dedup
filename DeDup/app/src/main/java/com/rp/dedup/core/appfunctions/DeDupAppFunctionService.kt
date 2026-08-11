package com.rp.dedup.core.appfunctions

import android.net.Uri
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.rp.dedup.feature.voicestorage.data.model.MediaType
import com.rp.dedup.feature.voicestorage.data.model.StorageItem
import com.rp.dedup.feature.voicestorage.data.repository.LocalStorageRepository
import com.rp.dedup.feature.voicestorage.domain.FilterConfig
import com.rp.dedup.feature.voicestorage.domain.SortBy
import com.rp.dedup.feature.voicestorage.domain.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** A single file returned by a read-only storage query. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class StorageFileResult(
    /** The file's display name, e.g. "IMG_20260101.jpg". */
    val displayName: String,
    /** The file's size in bytes. */
    val sizeInBytes: Long,
    /** The content URI identifying the file. */
    val uri: Uri,
)

/** Aggregate storage usage for one media type. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MediaTypeSummary(
    /** The media type this summary covers, e.g. "IMAGE", "VIDEO", "AUDIO". */
    val mediaType: String,
    /** Number of files of this type on the device. */
    val fileCount: Int,
    /** Combined size in bytes of all files of this type. */
    val totalSizeBytes: Long,
)

/**
 * Exposes DeDup's on-device storage queries to system agents, backed directly by
 * [LocalStorageRepository] (the same repository the in-app voice assistant uses) —
 * no duplicated query logic.
 *
 * Read-only by design: destructive actions (deleting files) are intentionally not
 * exposed here without an in-app confirmation step.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "DeDupAppFunctionService",
    appFunctionXmlFileName = "dedup_app_function_service",
)
abstract class BaseDeDupAppFunctionService : AppFunctionService() {

    /**
     * Finds the largest files on the device, sorted by size descending.
     *
     * @param minSizeBytes Only include files at least this large. 0 means no minimum.
     * @param maxResults Maximum number of files to return.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun findLargeFiles(
        minSizeBytes: Long = 0L,
        maxResults: Int = 20,
    ): List<StorageFileResult> = withContext(Dispatchers.IO) {
        validateFindLargeFilesParams(minSizeBytes, maxResults)

        LocalStorageRepository(this@BaseDeDupAppFunctionService)
            .queryFiles("", largeFilesFilterConfig(minSizeBytes))
            .first()
            .take(maxResults)
            .map(::toStorageFileResult)
    }

    /**
     * Finds photos added more than [olderThanDays] days ago, oldest first.
     *
     * @param olderThanDays Only include photos added at least this many days ago.
     * @param maxResults Maximum number of photos to return.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun findOldPhotos(
        olderThanDays: Int = 365,
        maxResults: Int = 20,
    ): List<StorageFileResult> = withContext(Dispatchers.IO) {
        validateFindOldPhotosParams(olderThanDays, maxResults)

        LocalStorageRepository(this@BaseDeDupAppFunctionService)
            .queryFiles("", oldPhotosFilterConfig(olderThanDays))
            .first()
            .take(maxResults)
            .map(::toStorageFileResult)
    }

    /** Summarizes on-device storage usage (file count and total size) per media type. */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getStorageSummary(): List<MediaTypeSummary> = withContext(Dispatchers.IO) {
        val items = LocalStorageRepository(this@BaseDeDupAppFunctionService)
            .queryFiles("", storageSummaryFilterConfig())
            .first()

        summarize(items)
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1_000L

        /** Pure/testable: builds the query filter for [findLargeFiles]. */
        internal fun largeFilesFilterConfig(minSizeBytes: Long): FilterConfig = FilterConfig(
            minSizeBytes = minSizeBytes.takeIf { it > 0L },
            mediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO),
            sortBy = SortBy.SIZE,
            sortOrder = SortOrder.DESC,
        )

        /** Pure/testable: validates [findLargeFiles] params, throwing on invalid input. */
        internal fun validateFindLargeFilesParams(minSizeBytes: Long, maxResults: Int) {
            if (minSizeBytes < 0L) {
                throw AppFunctionInvalidArgumentException("minSizeBytes must be non-negative")
            }
            if (maxResults <= 0) {
                throw AppFunctionInvalidArgumentException("maxResults must be positive")
            }
        }

        /** Pure/testable: builds the query filter for [findOldPhotos]. */
        internal fun oldPhotosFilterConfig(
            olderThanDays: Int,
            nowMs: Long = System.currentTimeMillis(),
        ): FilterConfig = FilterConfig(
            dateAddedBefore = nowMs - olderThanDays * DAY_MS,
            mediaTypes = setOf(MediaType.IMAGE),
            sortBy = SortBy.DATE_ADDED,
            sortOrder = SortOrder.ASC,
        )

        /** Pure/testable: validates [findOldPhotos] params, throwing on invalid input. */
        internal fun validateFindOldPhotosParams(olderThanDays: Int, maxResults: Int) {
            if (olderThanDays <= 0) {
                throw AppFunctionInvalidArgumentException("olderThanDays must be positive")
            }
            if (maxResults <= 0) {
                throw AppFunctionInvalidArgumentException("maxResults must be positive")
            }
        }

        /** Pure/testable: builds the query filter for [getStorageSummary]. */
        internal fun storageSummaryFilterConfig(): FilterConfig = FilterConfig(
            mediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO),
        )

        /** Pure/testable: aggregates items into one [MediaTypeSummary] per media type. */
        internal fun summarize(items: List<StorageItem>): List<MediaTypeSummary> =
            items.groupBy { it.mediaType }
                .map { (type, group) ->
                    MediaTypeSummary(
                        mediaType = type.name,
                        fileCount = group.size,
                        totalSizeBytes = group.sumOf { it.sizeInBytes },
                    )
                }

        /** Pure/testable: maps the repository's internal model to the AppFunction result type. */
        internal fun toStorageFileResult(item: StorageItem): StorageFileResult =
            StorageFileResult(item.displayName, item.sizeInBytes, item.uri)
    }
}
