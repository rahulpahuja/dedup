package com.rp.dedup.core.utils

import android.content.Context
import android.net.Uri
import java.io.File

object TrashManager {
    private const val TRASH_DIR = ".dedup_trash"

    fun moveToTrash(context: Context, uri: Uri): Boolean {
        return try {
            val trashDir = File(context.getExternalFilesDir(null), TRASH_DIR)
            if (!trashDir.exists()) trashDir.mkdirs()

            val sourceFile = File(getPathFromUri(context, uri) ?: return false)
            val destFile = File(trashDir, "${System.currentTimeMillis()}_${sourceFile.name}")

            if (sourceFile.renameTo(destFile)) {
                // Record the move in a database for restoration if needed
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getPathFromUri(context: Context, uri: Uri): String? {
        // Implementation for getting physical path from MediaStore Uri
        // This is a simplified version; real implementation needs careful handling of different Android versions
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA))
            }
        }
        return null
    }
}
