package com.rp.dedup.core.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.rp.dedup.core.data.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class FileScannerRepository(private val context: Context) {

    companion object {
        private const val TAG = "FileScannerRepository"
    }

    fun scanFilesByExtension(extensions: List<String>, deepScan: Boolean = false, excludedFolders: List<String> = emptyList()): Flow<ScannedFile> = flow {
        Log.d(TAG, "Starting scan for extensions: $extensions")
        
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA
        )

        // On modern Android, querying by DISPLAY_NAME is often more reliable than DATA (path)
        val selection = extensions.joinToString(separator = " OR ") { 
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?" 
        }
        val selectionArgs = extensions.map { "%.$it" }.toTypedArray()

        var count = 0
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn) ?: ""
                
                // Skip if path starts with any excluded folder
                if (excludedFolders.isNotEmpty() && excludedFolders.any { path.startsWith(it) }) {
                    continue
                }
                
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val size = cursor.getLong(sizeColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                val ext = path.substringAfterLast('.', "")

                val checksum = if (deepScan) com.rp.dedup.core.utils.ChecksumUtils.calculateMD5(context, uri) else null

                Log.d(TAG, "Found file: $name at $path")
                count++
                emit(ScannedFile(uri, name, size, path, ext, checksum))
            }
        }
        Log.d(TAG, "Scan finished. Total files found: $count")
    }.flowOn(Dispatchers.IO)
}
