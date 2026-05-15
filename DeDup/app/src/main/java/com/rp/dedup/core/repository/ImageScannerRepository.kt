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

    private val bitmapOptions = BitmapFactory.Options().apply {
        inSampleSize = 8
        inMutable = true
    }

    companion object {
        fun loadBitmapEfficiently(context: Context, uri: Uri, options: BitmapFactory.Options? = null): Bitmap? {
            return try {
                val effectiveOptions = options ?: BitmapFactory.Options().apply {
                    inSampleSize = 8
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, effectiveOptions)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun scanImagesInParallel(concurrencyLevel: Int = 8, excludedFolders: List<String> = emptyList()): Flow<ScannedImage> {
        val imageQueue = mutableListOf<Triple<Uri, Long, Long>>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn) ?: ""
                
                // Skip if path starts with any excluded folder
                if (excludedFolders.any { path.startsWith(it) }) continue

                val id = cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn)
                val date = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                imageQueue.add(Triple(uri, size, date))
            }
        }

        return imageQueue.asFlow()
            .flatMapMerge(concurrency = concurrencyLevel) { (uri, size, date) ->
                flow {
                    val bitmap = loadBitmapEfficiently(context, uri, bitmapOptions)
                    if (bitmap != null) {
                        val hash = ImageHasher.calculateDHash(bitmap)
                        bitmap.recycle() // Source bitmap still needs recycling as it's large/variable size
                        emit(ScannedImage(uri = uri.toString(), dHash = hash, sizeInBytes = size, dateModified = date))
                    }
                }
            }
            .flowOn(Dispatchers.Default)
    }
}
