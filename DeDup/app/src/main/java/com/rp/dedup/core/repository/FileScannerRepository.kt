package com.rp.dedup.core.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.rp.dedup.core.data.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class FileScannerRepository(private val context: Context) {

    fun scanFilesByExtension(extensions: List<String>): Flow<ScannedFile> = flow {
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

        // Create selection string like: ( _data LIKE '%.pdf' OR _data LIKE '%.apk' )
        val selection = extensions.joinToString(separator = " OR ") { "${MediaStore.Files.FileColumns.DATA} LIKE ?" }
        val selectionArgs = extensions.map { "%.$it" }.toTypedArray()

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
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val size = cursor.getLong(sizeColumn)
                val path = cursor.getString(dataColumn) ?: ""
                val uri = ContentUris.withAppendedId(collection, id)
                val ext = path.substringAfterLast('.', "")

                emit(ScannedFile(uri, name, size, path, ext))
            }
        }
    }.flowOn(Dispatchers.IO)
}
//
//class FileScannerRepository(private val context: Context) {
//
//    fun scanFilesByExtension(extensions: List<String>): Flow<ScannedFile> = flow {
//        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
//        } else {
//            MediaStore.Files.getContentUri("external")
//        }
//
//        val projection = arrayOf(
//            MediaStore.Files.FileColumns._ID,
//            MediaStore.Files.FileColumns.DISPLAY_NAME,
//            MediaStore.Files.FileColumns.SIZE,
//            MediaStore.Files.FileColumns.DATA
//        )
//
//        // Create selection string like: ( _data LIKE '%.pdf' OR _data LIKE '%.apk' )
//        val selection = extensions.joinToString(separator = " OR ") { "${MediaStore.Files.FileColumns.DATA} LIKE ?" }
//        val selectionArgs = extensions.map { "%.$it" }.toTypedArray()
//
//        context.contentResolver.query(
//            collection,
//            projection,
//            selection,
//            selectionArgs,
//            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
//        )?.use { cursor ->
//            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
//            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
//            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
//            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
//
//            while (cursor.moveToNext()) {
//                val id = cursor.getLong(idColumn)
//                val name = cursor.getString(nameColumn) ?: "Unknown"
//                val size = cursor.getLong(sizeColumn)
//                val path = cursor.getString(dataColumn) ?: ""
//                val uri = ContentUris.withAppendedId(collection, id)
//                val ext = path.substringAfterLast('.', "")
//
//                emit(ScannedFile(uri, name, size, path, ext))
//            }
//        }
//    }.flowOn(Dispatchers.IO)
//}