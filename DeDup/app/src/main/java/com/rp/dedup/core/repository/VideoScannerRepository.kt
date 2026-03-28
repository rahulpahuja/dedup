package com.rp.dedup.core.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.rp.dedup.core.common.Constants.EMPTY_STRING
import com.rp.dedup.core.data.ScannedVideo
import com.rp.dedup.core.common.VideoExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class VideoScannerRepository(private val context: Context) {

    fun scanVideos(): Flow<ScannedVideo> = flow {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val extension = name.substringAfterLast('.', EMPTY_STRING).lowercase()

                if (extension !in VideoExtensions.list) continue

                val id = cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn)
                val duration = cursor.getLong(durationColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: EMPTY_STRING
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )

                emit(
                    ScannedVideo(
                        uri = uri,
                        name = name,
                        sizeInBytes = size,
                        durationMs = duration,
                        mimeType = mimeType
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)
}

class VideoScannerRepository(private val context: Context) {

    fun scanVideos(): Flow<ScannedVideo> = flow {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val extension = name.substringAfterLast('.', EMPTY_STRING).lowercase()

                if (extension !in VideoExtensions.list) continue

                val id = cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn)
                val duration = cursor.getLong(durationColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: EMPTY_STRING
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )

                emit(
                    ScannedVideo(
                        uri = uri,
                        name = name,
                        sizeInBytes = size,
                        durationMs = duration,
                        mimeType = mimeType
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)
}