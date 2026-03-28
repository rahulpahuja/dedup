package com.rp.dedup.core.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.rp.dedup.core.image.ImageHasher
import com.rp.dedup.core.data.ScannedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

class ImageScannerRepository(private val context: Context) {

    companion object {
        fun loadBitmapEfficiently(context: Context, uri: Uri): Bitmap? {
            return try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 8
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun scanImagesInParallel(concurrencyLevel: Int = 8): Flow<ScannedImage> {
        val imageQueue = mutableListOf<Pair<Uri, Long>>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                imageQueue.add(Pair(uri, size))
            }
        }

        return imageQueue.asFlow()
            .flatMapMerge(concurrency = concurrencyLevel) { (uri, size) ->
                flow {
                    val bitmap = loadBitmapEfficiently(context, uri)
                    if (bitmap != null) {
                        val hash = ImageHasher.calculateDHash(bitmap)
                        bitmap.recycle()
                        emit(ScannedImage(uri = uri.toString(), dHash = hash, sizeInBytes = size))
                    }
                }
            }
            .flowOn(Dispatchers.Default)
    }
}
