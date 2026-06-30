package com.rp.dedup.core.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.os.Environment
import com.rp.dedup.core.model.ScannedFile
import com.rp.dedup.core.permissions.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class FileScannerRepository(private val context: Context) : IFileScannerRepository {

    companion object {
        private const val TAG = "FileScannerRepository"
    }

    fun scanFilesByExtension(extensions: List<String>, deepScan: Boolean = false, excludedFolders: List<String> = emptyList()): Flow<ScannedFile> = flow<ScannedFile> {
        Log.d(TAG, "Starting scan for extensions: $extensions")
        
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        // Query by extension and common MIME types
        val selectionBuilder = StringBuilder()
        val args = mutableListOf<String>()

        extensions.forEachIndexed { index, ext ->
            if (index > 0) selectionBuilder.append(" OR ")
            selectionBuilder.append("(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?")
            args.add("%.$ext")
            
            val mime = when (ext.lowercase()) {
                "pdf" -> "application/pdf"
                "apk" -> "application/vnd.android.package-archive"
                else -> null
            }
            if (mime != null) {
                selectionBuilder.append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} = ?")
                args.add(mime)
            }
            selectionBuilder.append(")")
        }

        val selection = selectionBuilder.toString()
        val selectionArgs = args.toTypedArray()

        var count = 0
        val foundPaths = mutableSetOf<String>()

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
                val ext = name.substringAfterLast('.', "")

                val checksum = if (deepScan) com.rp.dedup.core.utils.ChecksumUtils.calculateSHA256(context, uri) else null

                Log.d(TAG, "Found via MediaStore: $name at $path")
                count++
                foundPaths.add(path)
                emit(ScannedFile(uri, name, size, path, ext, checksum))
            }
        }

        // Fallback: If MANAGE_EXTERNAL_STORAGE is granted, do a direct file system scan
        // to find files that MediaStore might have missed (common for non-media files)
        val permissionManager = PermissionManager(context)
        if (permissionManager.hasAllFilesAccess) {
            Log.d(TAG, "All Files Access granted, starting direct filesystem scan fallback")
            val externalStorage = Environment.getExternalStorageDirectory()
            emitDirectoryRecursively(externalStorage, extensions, foundPaths, excludedFolders) { file ->
                count++
                emit(file)
            }
        }

        Log.d(TAG, "Scan finished. Total files found: $count")
    }.flowOn(Dispatchers.IO)

    fun scanOldFiles(folder: String, olderThanMs: Long): Flow<ScannedFile> = flow {
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ? ESCAPE '\\' AND ${MediaStore.Files.FileColumns.DATE_MODIFIED} < ?"
        val escapedFolder = folder.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val selectionArgs = arrayOf("$escapedFolder%", (olderThanMs / 1000).toString())

        context.contentResolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} ASC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn) ?: ""
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val size = cursor.getLong(sizeColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                val ext = name.substringAfterLast('.', "")

                emit(ScannedFile(uri, name, size, path, ext))
            }
        }
    }.flowOn(Dispatchers.IO)

    // Iterative DFS — emits each matching file immediately instead of accumulating
    // a potentially huge list. Avoids OOM on large external storage trees.
    private suspend fun emitDirectoryRecursively(
        root: File,
        extensions: List<String>,
        alreadyFoundPaths: Set<String>,
        excludedFolders: List<String>,
        emit: suspend (ScannedFile) -> Unit
    ) {
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            if (dir.name.startsWith(".") || excludedFolders.any { dir.absolutePath.startsWith(it) }) continue
            val children = dir.listFiles() ?: continue
            for (file in children) {
                if (file.isDirectory) {
                    stack.addLast(file)
                } else {
                    val path = file.absolutePath
                    if (path !in alreadyFoundPaths) {
                        val ext = path.substringAfterLast('.', "").lowercase()
                        if (ext in extensions) {
                            emit(ScannedFile(Uri.fromFile(file), file.name, file.length(), path, ext))
                        }
                    }
                }
            }
        }
    }
}
