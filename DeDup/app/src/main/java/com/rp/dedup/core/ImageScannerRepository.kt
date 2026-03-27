package com.rp.dedup.core

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
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

    @OptIn(FlowPreview::class) // flatMapMerge requires this opt-in
    fun scanImagesInParallel(concurrencyLevel: Int = 8): Flow<ScannedImage> {

        // Step 1: Fast Query
        // Fetch ALL URIs and Sizes instantly. This takes milliseconds and uses
        // almost zero memory because we are only storing strings/numbers, not Bitmaps.
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

        // Step 2: The Parallel Pipeline
        return imageQueue.asFlow()
            // flatMapMerge creates our 4 parallel threads!
            .flatMapMerge(concurrency = concurrencyLevel) { (uri, size) ->
                flow {
                    // This block now runs concurrently across 4 threads
                    val bitmap = loadBitmapEfficiently(context, uri)
                    if (bitmap != null) {
                        val hash = ImageHasher.calculateDHash(bitmap)
                        bitmap.recycle() // Free memory instantly

                        // Emit the result to the UI
                        emit(ScannedImage(uri = uri, dHash = hash, sizeInBytes = size))
                    }
                }
            }
            // Use Dispatchers.Default (not IO) because calculating the Hamming Distance
            // and dHash is a heavy mathematical/CPU-bound task.
            .flowOn(Dispatchers.Default)
    }
}