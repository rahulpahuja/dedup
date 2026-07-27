package com.rp.dedup.core.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.rp.dedup.core.common.Constants.EMPTY_STRING
import com.rp.dedup.core.model.ScannedVideo
import com.rp.dedup.core.common.VideoExtensions
import com.rp.dedup.core.video.VideoFrameHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class VideoScannerRepository(private val context: Context) : IVideoScannerRepository {

    override fun scanVideos(deepScan: Boolean): Flow<ScannedVideo> = flow {
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.VOLUME_NAME)
            }
        }.toTypedArray()
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
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val volumeColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME) else -1

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val extension = name.substringAfterLast('.', EMPTY_STRING).lowercase()

                if (extension !in VideoExtensions.list) continue

                val id = cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn)
                val duration = cursor.getLong(durationColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: EMPTY_STRING
                val path = cursor.getString(dataColumn)
                val baseUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && volumeColumn >= 0) {
                    val volume = cursor.getString(volumeColumn) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                    MediaStore.Video.Media.getContentUri(volume)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                val uri = ContentUris.withAppendedId(baseUri, id)

                val frameHashes = if (deepScan && duration > 0) {
                    VideoFrameHasher.calculateFrameHashes(context, uri, duration)
                } else {
                    emptyList()
                }

                emit(
                    ScannedVideo(
                        uri = uri,
                        name = name,
                        sizeInBytes = size,
                        durationMs = duration,
                        mimeType = mimeType,
                        frameHashes = frameHashes,
                        path = path
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)
}