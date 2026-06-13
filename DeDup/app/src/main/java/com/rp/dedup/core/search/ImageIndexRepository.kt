package com.rp.dedup.core.search

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.rp.dedup.core.dao.ImageEmbeddingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Builds and maintains the semantic index stored in Room.
 *
 * For each image it:
 *   1. Reads DISPLAY_NAME, BUCKET_DISPLAY_NAME, RELATIVE_PATH, DATE_TAKEN from MediaStore.
 *   2. Assembles a short text description from those fields.
 *   3. Embeds that description via [embedder].
 *   4. Upserts the result into [dao].
 *
 * Stale rows (images deleted from the device) are pruned on every run.
 */
class ImageIndexRepository(
    private val context: Context,
    private val dao: ImageEmbeddingDao,
    private val embedder: EmbedderProvider
) {

    companion object {
        private const val TAG = "ImageIndexRepo"
        private const val BATCH_SIZE = 50
    }

    suspend fun indexImages(
        onProgress: (indexed: Int, total: Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {

        if (!embedder.isAvailable) {
            Log.w(TAG, "Embedder not available — skipping indexing run")
            return@withContext
        }

        val allUris      = loadAllImageUris()
        val existingUris = dao.getAllUris().toHashSet()

        // Remove embeddings for images no longer on the device
        if (allUris.isNotEmpty()) {
            dao.deleteStale(allUris.map { it.toString() })
        }

        val newUris = allUris.filter { it.toString() !in existingUris }
        Log.d(TAG, "${newUris.size} new images to index (${existingUris.size} already indexed)")

        var indexed = 0
        newUris.chunked(BATCH_SIZE).forEach { batch ->
            val entities = batch.mapNotNull { uri -> buildEntity(uri) }
            if (entities.isNotEmpty()) dao.insertAll(entities)
            indexed += batch.size
            onProgress(indexed, newUris.size)
        }

        Log.d(TAG, "Indexing complete — $indexed images processed")
    }

    private fun buildEntity(uri: Uri): ImageEmbeddingEntity? {
        val projection = arrayOf(
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_TAKEN
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null

            val displayName  = cursor.getString(0).orEmpty()
            val bucketName   = cursor.getString(1).orEmpty()
            val relativePath = cursor.getString(2).orEmpty()
            val dateTaken    = if (cursor.isNull(3)) null else cursor.getLong(3)

            val description = buildDescription(bucketName, relativePath, dateTaken)
            val embedding   = embedder.embed(description) ?: return null

            return ImageEmbeddingEntity(
                uri         = uri.toString(),
                displayName = displayName,
                bucketName  = bucketName,
                description = description,
                embedding   = embedding
            )
        }
        return null
    }

    /**
     * Builds a short natural-language description from image metadata.
     * Example: "whatsapp images, march 2024"
     * Example: "camera, dcim, january 2024"
     */
    private fun buildDescription(
        bucketName: String,
        relativePath: String,
        dateTaken: Long?
    ): String {
        val parts = buildList<String> {
            if (bucketName.isNotBlank()) add(bucketName.replace("_", " "))

            // Normalize path: "DCIM/Camera/" → ["dcim", "camera"]
            relativePath.split("/")
                .filter { seg ->
                    seg.isNotBlank() && seg.lowercase() !in setOf("storage", "emulated", "0", "sdcard")
                }
                .forEach { add(it.replace("_", " ")) }

            dateTaken?.let {
                val cal = Calendar.getInstance().apply { timeInMillis = it }
                add(SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time))
            }
        }
        return parts.distinct().joinToString(", ").lowercase()
    }

    private fun loadAllImageUris(): List<Uri> {
        val uris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                uris.add(
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(col)
                    )
                )
            }
        }
        return uris
    }
}
