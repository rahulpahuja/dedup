package com.rp.dedup.core.trash

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.rp.dedup.core.model.TrashItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Handles the file-level operations for the Trash system.
 * Files are copied to the app's private filesDir/trash/ directory before the original
 * is deleted, so they survive without any external storage permission on restore.
 */
class TrashManager(private val context: Context) {

    companion object {
        private const val TAG = "TrashManager"
        private const val TRASH_DIR = "trash"
        private const val EXPIRES_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }

    private val trashDir: File get() =
        File(context.filesDir, TRASH_DIR).also { it.mkdirs() }

    /**
     * Copies [uri] bytes to private storage and returns a [TrashItem] ready to insert.
     * The caller is responsible for deleting the original via ContentResolver and inserting
     * the returned item into the database.
     * Returns null if the file cannot be read (permission revoked, file already gone, etc).
     */
    suspend fun prepareTrashItem(uri: Uri): TrashItem? = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA,
        )
        val cursor = cr.query(uri, projection, null, null, null) ?: return@withContext null
        val (name, size, mime, path) = cursor.use { c ->
            if (!c.moveToFirst()) return@withContext null
            val n = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)) ?: ""
            val s = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
            val m = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)) ?: ""
            val p = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)) ?: ""
            arrayOf(n, s.toString(), m, p)
        }

        val trashFileName = "${UUID.randomUUID()}_$name"
        val destFile = File(trashDir, trashFileName)

        try {
            cr.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy $uri to trash", e)
            destFile.delete()
            return@withContext null
        }

        val now = System.currentTimeMillis()
        val mediaType = when {
            mime.startsWith("image/") -> "IMAGE"
            mime.startsWith("video/") -> "VIDEO"
            else -> "FILE"
        }

        TrashItem(
            originalUri   = uri.toString(),
            originalPath  = path,
            name          = name,
            size          = size.toLong(),
            mimeType      = mime,
            mediaType     = mediaType,
            trashedAtMs   = now,
            expiresAtMs   = now + EXPIRES_MS,
            trashFileName = trashFileName
        )
    }

    /**
     * Restores [item] by re-inserting its bytes into MediaStore and deleting the private copy.
     * Returns true on success.
     */
    suspend fun restore(item: TrashItem): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = File(trashDir, item.trashFileName)
        if (!sourceFile.exists()) {
            Log.w(TAG, "Trash file not found: ${item.trashFileName}")
            return@withContext false
        }

        val cr = context.contentResolver
        val collection = when {
            item.mimeType.startsWith("image/") -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            item.mimeType.startsWith("video/") -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Files.getContentUri("external")
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val insertedUri = try {
            cr.insert(collection, values)
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore insert failed", e)
            return@withContext false
        } ?: return@withContext false

        val copied = try {
            cr.openOutputStream(insertedUri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Byte copy failed", e)
            cr.delete(insertedUri, null, null)
            false
        }

        if (copied && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val clear = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            cr.update(insertedUri, clear, null, null)
        }

        if (copied) sourceFile.delete()
        copied
    }

    /** Permanently removes the private file for [item] without restoring it. */
    suspend fun deleteForever(item: TrashItem) = withContext(Dispatchers.IO) {
        File(trashDir, item.trashFileName).delete()
    }

    /** Deletes private files for all expired items. */
    suspend fun pruneExpiredFiles(expiredItems: List<TrashItem>) = withContext(Dispatchers.IO) {
        expiredItems.forEach { File(trashDir, it.trashFileName).delete() }
    }

    /** Opens an InputStream for the cached trash copy — used by thumbnail loaders. */
    fun openTrashInputStream(trashFileName: String) =
        File(trashDir, trashFileName).takeIf { it.exists() }?.inputStream()
}
