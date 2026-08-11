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

    companion object {
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

        /** Pure/testable: maps the repository's internal model to the AppFunction result type. */
        internal fun toStorageFileResult(item: StorageItem): StorageFileResult =
            StorageFileResult(item.displayName, item.sizeInBytes, item.uri)
    }
}
