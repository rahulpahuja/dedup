package com.rp.dedup.core.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi

object TrashManager {
    private const val TRASH_DIR = ".dedup_trash"

    /**
     * Moves [uri] to trash.
     *
     * - API 30+: Uses MediaStore.createTrashRequest (system-level trash, recoverable).
     *   Returns true if the request was created successfully; the actual confirmation
     *   dialog is shown by the caller via Activity.startIntentSenderForResult.
     * - API 29: Copies file bytes to app-private trash dir, then deletes via ContentResolver.
     * - API ≤ 28: Moves via File.renameTo on the raw filesystem (WRITE_EXTERNAL_STORAGE).
     *
     * Returns false only when the operation definitely failed (e.g., source not found).
     * On API 30+ the return value signals whether the pending intent was built — the user
     * must still confirm via the system dialog.
     */
    fun moveToTrash(context: Context, uri: Uri): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> trashViaMediaStore(context, uri)
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> trashViaCopy(context, uri)
            else -> trashViaFile(context, uri)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun trashViaMediaStore(context: Context, uri: Uri): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_TRASHED, 1)
            }
            val updated = context.contentResolver.update(uri, values, null, null)
            updated > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun trashViaCopy(context: Context, uri: Uri): Boolean {
        return try {
            val trashDir = java.io.File(context.getExternalFilesDir(null), TRASH_DIR)
                .also { if (!it.exists()) it.mkdirs() }

            val fileName = queryDisplayName(context, uri) ?: return false
            val destFile = java.io.File(trashDir, "${System.currentTimeMillis()}_$fileName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return false

            // Delete original via ContentResolver — safe on API 29 scoped storage
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun trashViaFile(context: Context, uri: Uri): Boolean {
        return try {
            val trashDir = java.io.File(context.getExternalFilesDir(null), TRASH_DIR)
                .also { if (!it.exists()) it.mkdirs() }

            val sourcePath = queryDataColumn(context, uri) ?: return false
            val sourceFile = java.io.File(sourcePath)
            val destFile = java.io.File(trashDir, "${System.currentTimeMillis()}_${sourceFile.name}")

            sourceFile.renameTo(destFile)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun queryDataColumn(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}
