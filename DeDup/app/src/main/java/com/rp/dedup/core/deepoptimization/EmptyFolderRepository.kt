package com.rp.dedup.core.deepoptimization

import android.content.Context
import android.os.Environment
import com.rp.dedup.core.data.EmptyFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface EmptyFolderRepository {
    suspend fun findEmptyFolders(): List<EmptyFolder>
    suspend fun deleteFolder(folder: EmptyFolder): Boolean
}

class EmptyFolderRepositoryImpl(private val context: Context) : EmptyFolderRepository {

    private val externalRoot = Environment.getExternalStorageDirectory()

    override suspend fun findEmptyFolders(): List<EmptyFolder> = withContext(Dispatchers.IO) {
        externalRoot
            .walkTopDown()
            .filter { it.isDirectory && it != externalRoot && !it.name.startsWith(".") }
            .filter { isEffectivelyEmpty(it) }
            .map { dir ->
                EmptyFolder(
                    path = dir.absolutePath,
                    name = dir.name,
                    parentPath = dir.parent ?: ""
                )
            }
            .sortedByDescending { it.path.length } // deepest first for safe deletion
            .toList()
    }

    override suspend fun deleteFolder(folder: EmptyFolder): Boolean = withContext(Dispatchers.IO) {
        File(folder.path).delete()
    }

    // A directory is effectively empty if it contains no files and all subdirectories are also effectively empty
    private fun isEffectivelyEmpty(dir: File): Boolean {
        val children = dir.listFiles() ?: return true
        return children.all { child -> child.isDirectory && isEffectivelyEmpty(child) }
    }
}
