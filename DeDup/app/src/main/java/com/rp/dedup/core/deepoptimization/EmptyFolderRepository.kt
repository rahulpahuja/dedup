package com.rp.dedup.core.deepoptimization

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.rp.dedup.core.common.Constants
import com.rp.dedup.core.model.EmptyFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface EmptyFolderRepository {
    // treeUri: SAF tree URI granted by the user on Android 11+; null on older devices.
    suspend fun findEmptyFolders(treeUri: Uri?): List<EmptyFolder>
    suspend fun deleteFolder(folder: EmptyFolder): Boolean
}

class EmptyFolderRepositoryImpl(private val context: Context) : EmptyFolderRepository {

    override suspend fun findEmptyFolders(treeUri: Uri?): List<EmptyFolder> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && treeUri != null) {
            findEmptyFoldersSAF(treeUri)
        } else {
            findEmptyFoldersFileApi()
        }
    }

    override suspend fun deleteFolder(folder: EmptyFolder): Boolean = withContext(Dispatchers.IO) {
        if (folder.documentUri != null) {
            DocumentFile.fromSingleUri(context, Uri.parse(folder.documentUri))?.delete() ?: false
        } else {
            File(folder.path).delete()
        }
    }

    // Android 11+: traverse via DocumentFile (no MANAGE_EXTERNAL_STORAGE required).
    private fun findEmptyFoldersSAF(treeUri: Uri): List<EmptyFolder> {
        val root   = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val result = mutableListOf<EmptyFolder>()
        collectEmptyFoldersSAF(root, result)
        return result.sortedByDescending { it.path.length } // deepest first — safe deletion order
    }

    private fun collectEmptyFoldersSAF(dir: DocumentFile, result: MutableList<EmptyFolder>) {
        if (!dir.isDirectory) return
        val children = dir.listFiles()
        if (isEffectivelyEmptySAF(dir)) {
            result += EmptyFolder(
                path        = dir.uri.toString(),
                name        = dir.name ?: "Unknown",
                parentPath  = dir.parentFile?.uri?.toString() ?: "",
                documentUri = dir.uri.toString()
            )
            return
        }
        // Only recurse if the directory itself is not empty — avoids double-listing.
        children.filter { it.isDirectory }.forEach { collectEmptyFoldersSAF(it, result) }
    }

    private fun isEffectivelyEmptySAF(dir: DocumentFile): Boolean {
        val children = dir.listFiles()
        return children.isEmpty() || children.all { it.isDirectory && isEffectivelyEmptySAF(it) }
    }

    // Android < 11: File API is fine with READ_EXTERNAL_STORAGE (no MANAGE needed).
    private fun findEmptyFoldersFileApi(): List<EmptyFolder> {
        val externalRoot = Environment.getExternalStorageDirectory()
        return externalRoot
            .walkTopDown()
            .filter { it.isDirectory && it != externalRoot && !it.name.startsWith(".") }
            .filter { isEffectivelyEmptyFileApi(it) }
            .map { dir ->
                EmptyFolder(
                    path       = dir.absolutePath,
                    name       = dir.name,
                    parentPath = dir.parent ?: Constants.EMPTY_STRING
                )
            }
            .sortedByDescending { it.path.length }
            .toList()
    }

    private fun isEffectivelyEmptyFileApi(dir: File): Boolean {
        val children = dir.listFiles() ?: return true
        return children.all { child -> child.isDirectory && isEffectivelyEmptyFileApi(child) }
    }
}
