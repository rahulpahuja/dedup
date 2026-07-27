package com.rp.dedup.core.compression

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CompressionResult(
    val uri: Uri,
    val name: String,
    val originalSize: Long,
    val compressedSize: Long,
    val savedBytes: Long = originalSize - compressedSize
)

class ImageCompressionRepository(private val context: Context) {

    companion object {
        private const val TAG = "ImageCompressRepo"

        // Caps the decoded bitmap's long side so an extreme-resolution source (e.g. a
        // 108MP photo) can't blow past a bounded allocation; ~46MB worst case at this cap.
        private const val MAX_DECODE_DIMENSION = 3500

        private fun calculateInSampleSizeCappedAt(srcW: Int, srcH: Int, maxDim: Int): Int {
            val longSide = maxOf(srcW, srcH)
            if (longSide <= maxDim) return 1
            var sample = 1
            while (longSide / (sample * 2) >= maxDim) sample *= 2
            return sample
        }
    }

    /**
     * Returns all images from MediaStore above [minSizeBytes] sorted by size descending.
     * Includes original URI, name, and size for the pre-compression list screen.
     */
    suspend fun loadCompressibleImages(
        minSizeBytes: Long = 500_000L
    ): List<Triple<Uri, String, Long>> = withContext(Dispatchers.IO) {

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val selection = "${MediaStore.Images.Media.SIZE} > ? AND (${MediaStore.Images.Media.MIME_TYPE} LIKE 'image/jpeg' OR ${MediaStore.Images.Media.MIME_TYPE} LIKE 'image/png')"
        val selectionArgs = arrayOf(minSizeBytes.toString())
        val sortOrder = "${MediaStore.Images.Media.SIZE} DESC"

        val results = mutableListOf<Triple<Uri, String, Long>>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            while (cursor.moveToNext()) {
                val id   = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val uri  = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                results.add(Triple(uri, name, size))
            }
        }
        results
    }

    /**
     * Compresses [uri] at [quality] (0-100, WebP lossy) and inserts the result as a new
     * MediaStore image.  Returns a [CompressionResult] with the actual saved bytes, or null
     * on failure.
     */
    suspend fun compress(uri: Uri, quality: Int, deleteOriginal: Boolean): CompressionResult? =
        withContext(Dispatchers.IO) {

        val cr = context.contentResolver
        val meta = cr.query(
            uri,
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE
            ),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) Triple(
                c.getString(0) ?: "image.jpg",
                c.getLong(1),
                c.getString(2)
            ) else null
        } ?: return@withContext null

        val (originalName, originalSize, mimeType) = meta

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSizeCappedAt(
                boundsOpts.outWidth, boundsOpts.outHeight, MAX_DECODE_DIMENSION
            )
            // PNGs may carry alpha; only drop to RGB_565 for JPEGs, which never do.
            if (mimeType == "image/jpeg") inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return@withContext null

        val baseName = originalName.substringBeforeLast(".")
        val newName  = "${baseName}_dedup_q$quality.webp"

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, newName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val insertedUri = cr.insert(collection, values) ?: run {
            bitmap.recycle(); return@withContext null
        }

        val compressedSize: Long = try {
            cr.openOutputStream(insertedUri)?.use { out ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Bitmap.CompressFormat.WEBP_LOSSY
                else
                    @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                bitmap.compress(format, quality, out)
            }
            // Re-query actual size
            cr.query(insertedUri, arrayOf(MediaStore.Images.Media.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed for $uri", e)
            cr.delete(insertedUri, null, null)
            bitmap.recycle()
            return@withContext null
        } finally {
            bitmap.recycle()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val clear = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            cr.update(insertedUri, clear, null, null)
        }

        if (deleteOriginal) {
            try { cr.delete(uri, null, null) } catch (e: Exception) {
                Log.w(TAG, "Could not delete original $uri", e)
            }
        }

        CompressionResult(
            uri            = insertedUri,
            name           = newName,
            originalSize   = originalSize,
            compressedSize = compressedSize
        )
    }
}
