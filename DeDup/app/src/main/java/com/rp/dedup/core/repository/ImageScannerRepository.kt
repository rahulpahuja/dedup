package com.rp.dedup.core.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.rp.dedup.core.image.ImageHasher
import com.rp.dedup.core.data.ScannedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.util.zip.CRC32

class ImageScannerRepository(private val context: Context) {

    companion object {
        // Minimum decoded dimension for hashing: 16x the 8-row dHash target.
        // Ensures the Canvas downscale is always a true reduction, never an upscale.
        private const val HASH_DECODE_TARGET = 128

        /**
         * Two-pass efficient bitmap loader:
         *  Pass 1 — read dimensions only (zero pixel allocation).
         *  Pass 2 — decode at the largest power-of-2 subsample that still keeps the
         *           minimum dimension ≥ targetWidth, then correct EXIF orientation.
         *
         * Use targetWidth = HASH_DECODE_TARGET (128) for hashing; leave it at the
         * default (500) for quality scoring / face detection.
         */
        fun loadBitmapEfficiently(context: Context, uri: Uri, targetWidth: Int = 500): Bitmap? {
            return try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

                val srcW = boundsOpts.outWidth
                val srcH = boundsOpts.outHeight
                if (srcW <= 0 || srcH <= 0) return null

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(srcW, srcH, targetWidth)
                }
                val raw = context.contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                    ?: return null

                applyExifOrientation(context, uri, raw)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * CRC32 of the first 64 KB of the file.
         * Sufficient to uniquely identify exact-duplicate files in practice without
         * reading entire multi-megabyte images. Returns -1 on error.
         */
        fun computePartialCrc32(context: Context, uri: Uri): Long {
            return try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val crc = CRC32()
                    val buffer = ByteArray(65536)
                    val read = stream.read(buffer)
                    if (read > 0) crc.update(buffer, 0, read)
                    crc.value
                } ?: -1L
            } catch (e: Exception) {
                -1L
            }
        }

        // Largest power-of-2 subsample where decoded min-dimension still >= reqSize.
        private fun calculateInSampleSize(srcW: Int, srcH: Int, reqSize: Int): Int {
            val minDim = minOf(srcW, srcH)
            if (minDim <= reqSize) return 1
            var sample = 1
            while (minDim / (sample * 2) >= reqSize) sample *= 2
            return sample
        }

        private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
            val degrees = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                0f
            }
            if (degrees == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            return rotated
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun scanImagesInParallel(
        concurrencyLevel: Int = 8,
        excludedFolders: List<String> = emptyList()
    ): Flow<ScannedImage> {
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
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn) ?: ""
                if (excludedFolders.any { path.startsWith(it) }) continue

                val id = cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn)
                val date = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                imageQueue.add(Triple(uri, size, date))
            }
        }

        return imageQueue.asFlow()
            .flatMapMerge(concurrency = concurrencyLevel) { (uri, size, date) ->
                flow {
                    // Decode at 128px minimum — 16x the dHash target height (8px).
                    // This guarantees the Canvas always downscales (never upscales),
                    // producing stable gradients for accurate hashing.
                    val bitmap = loadBitmapEfficiently(context, uri, targetWidth = HASH_DECODE_TARGET)
                    if (bitmap != null) {
                        val hash = ImageHasher.calculateDHash(bitmap)
                        bitmap.recycle()
                        val exactHash = computePartialCrc32(context, uri)
                        emit(
                            ScannedImage(
                                uri = uri.toString(),
                                dHash = hash,
                                sizeInBytes = size,
                                dateModified = date,
                                exactHash = exactHash
                            )
                        )
                    }
                }
            }
            .flowOn(Dispatchers.Default)
    }
}
