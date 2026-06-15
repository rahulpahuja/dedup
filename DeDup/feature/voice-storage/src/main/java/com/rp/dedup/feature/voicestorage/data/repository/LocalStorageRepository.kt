package com.rp.dedup.feature.voicestorage.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.rp.dedup.feature.voicestorage.data.model.MediaType
import com.rp.dedup.feature.voicestorage.data.model.StorageItem
import com.rp.dedup.feature.voicestorage.domain.FilterConfig
import com.rp.dedup.feature.voicestorage.domain.SortBy
import com.rp.dedup.feature.voicestorage.domain.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class LocalStorageRepository(private val context: Context) {

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
        )
    }

    private fun sortOrder(filters: FilterConfig): String {
        val column = when (filters.sortBy) {
            SortBy.SIZE       -> MediaStore.MediaColumns.SIZE
            SortBy.NAME       -> MediaStore.MediaColumns.DISPLAY_NAME
            SortBy.DATE_ADDED -> MediaStore.MediaColumns.DATE_ADDED
        }
        val direction = if (filters.sortOrder == SortOrder.DESC) "DESC" else "ASC"
        return "$column $direction"
    }

    fun queryFiles(query: String, filters: FilterConfig): Flow<List<StorageItem>> = flow {
        val order = sortOrder(filters)
        val results = buildList {
            if (MediaType.IMAGE in filters.mediaTypes) {
                addAll(queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE, query, filters, order))
            }
            if (MediaType.VIDEO in filters.mediaTypes) {
                addAll(queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO, query, filters, order))
            }
            if (MediaType.AUDIO in filters.mediaTypes) {
                addAll(queryMedia(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaType.AUDIO, query, filters, order))
            }
            // DOCUMENT always needs a mimeTypeFilter — querying all Files without one would return everything
            if (MediaType.DOCUMENT in filters.mediaTypes && filters.mimeTypeFilter != null) {
                addAll(queryMedia(MediaStore.Files.getContentUri("external"), MediaType.DOCUMENT, query, filters, order))
            }
        }.let { list ->
            // Re-sort the merged image+video list in memory to honour the user's sort intent
            when (filters.sortBy) {
                SortBy.SIZE       -> if (filters.sortOrder == SortOrder.DESC) list.sortedByDescending { it.sizeInBytes }
                                     else list.sortedBy { it.sizeInBytes }
                SortBy.NAME       -> if (filters.sortOrder == SortOrder.DESC) list.sortedByDescending { it.displayName }
                                     else list.sortedBy { it.displayName }
                SortBy.DATE_ADDED -> if (filters.sortOrder == SortOrder.DESC) list.sortedByDescending { it.dateAdded }
                                     else list.sortedBy { it.dateAdded }
            }
        }
        emit(results)
    }.flowOn(Dispatchers.IO)

    private fun queryMedia(
        contentUri: android.net.Uri,
        mediaType: MediaType,
        query: String,
        filters: FilterConfig,
        sortOrder: String,
    ): List<StorageItem> {
        // Dynamically compile selection and args from the structured filter config
        val selectionClauses = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (query.isNotBlank()) {
            selectionClauses += "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            selectionArgs += "%${query.trim()}%"
        }
        filters.minSizeBytes?.let {
            selectionClauses += "${MediaStore.MediaColumns.SIZE} >= ?"
            selectionArgs += it.toString()
        }
        filters.maxSizeBytes?.let {
            selectionClauses += "${MediaStore.MediaColumns.SIZE} <= ?"
            selectionArgs += it.toString()
        }
        filters.mimeTypeFilter?.let {
            selectionClauses += "${MediaStore.MediaColumns.MIME_TYPE} = ?"
            selectionArgs += it
        }
        // MediaStore DATE_ADDED is in seconds; FilterConfig carries epoch millis
        filters.dateAddedAfter?.let {
            selectionClauses += "${MediaStore.MediaColumns.DATE_ADDED} >= ?"
            selectionArgs += (it / 1000L).toString()
        }
        filters.dateAddedBefore?.let {
            selectionClauses += "${MediaStore.MediaColumns.DATE_ADDED} <= ?"
            selectionArgs += (it / 1000L).toString()
        }

        val selection = selectionClauses.joinToString(" AND ").ifEmpty { null }
        val args = selectionArgs.toTypedArray().ifEmpty { null }

        return context.contentResolver.query(
            contentUri, PROJECTION, selection, args, sortOrder
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

            buildList {
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(contentUri, cursor.getLong(idCol))
                    add(
                        StorageItem(
                            uri         = uri,
                            displayName = cursor.getString(nameCol).orEmpty(),
                            sizeInBytes = cursor.getLong(sizeCol),
                            dateAdded   = cursor.getLong(dateCol) * 1_000L,
                            mimeType    = cursor.getString(mimeCol).orEmpty(),
                            mediaType   = mediaType,
                        )
                    )
                }
            }
        } ?: emptyList()
    }
}
