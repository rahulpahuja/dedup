package com.rp.dedup.core.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.rp.dedup.core.common.Constants.EMPTY_STRING
import com.rp.dedup.core.model.ScannedVideo
import com.rp.dedup.core.common.VideoExtensions
import com.rp.dedup.core.video.VideoFrameHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull

class VideoScannerRepository(private val context: Context) : IVideoScannerRepository {

    companion object {
        // Frame extraction (MediaMetadataRetriever) is the slow step. Running several
        // videos concurrently — instead of one at a time — is what actually makes the
        // scan fast; most hardware decoders comfortably support this many concurrent
        // sessions without starving any single one.
        private const val SCAN_CONCURRENCY = 3

        // Guards against a single corrupt/DRM-protected/unreadable video hanging its
        // MediaMetadataRetriever call forever and stalling the whole scan (the main
        // source of "scan gets stuck" reports).
        private const val FRAME_HASH_TIMEOUT_MS = 8_000L
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun scanVideos(deepScan: Boolean): Flow<ScannedVideo> = flow {
        val videoQueue = mutableListOf<VideoMeta>()

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

                videoQueue.add(VideoMeta(uri, name, size, duration, mimeType, path))
            }
        }

        val hashingFlow = videoQueue.asFlow()
            .flatMapMerge(concurrency = SCAN_CONCURRENCY) { meta ->
                flow {
                    val frameHashes = if (deepScan && meta.duration > 0) {
                        try {
                            withTimeoutOrNull(FRAME_HASH_TIMEOUT_MS) {
                                VideoFrameHasher.calculateFrameHashes(context, meta.uri, meta.duration)
                            } ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }

                    emit(
                        ScannedVideo(
                            uri = meta.uri,
                            name = meta.name,
                            sizeInBytes = meta.size,
                            durationMs = meta.duration,
                            mimeType = meta.mimeType,
                            frameHashes = frameHashes,
                            path = meta.path
                        )
                    )
                }
            }

        emitAll(hashingFlow)
    }.flowOn(Dispatchers.IO)

    private data class VideoMeta(
        val uri: Uri,
        val name: String,
        val size: Long,
        val duration: Long,
        val mimeType: String,
        val path: String?
    )
}
