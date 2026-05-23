package com.rp.dedup.core.deepoptimization

import android.content.Context
import android.os.Environment
import com.rp.dedup.core.data.FolderNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface StorageTreeRepository {
    suspend fun buildTree(maxDepth: Int = 3): FolderNode
}

class StorageTreeRepositoryImpl(private val context: Context) : StorageTreeRepository {

    private val externalRoot = Environment.getExternalStorageDirectory()

    override suspend fun buildTree(maxDepth: Int): FolderNode = withContext(Dispatchers.IO) {
        buildNode(externalRoot, currentDepth = 0, maxDepth = maxDepth)
    }

    private fun buildNode(dir: File, currentDepth: Int, maxDepth: Int): FolderNode {
        val displayName = if (dir == externalRoot) "Internal Storage" else dir.name

        val children = if (currentDepth < maxDepth) {
            dir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.map { buildNode(it, currentDepth + 1, maxDepth) }
                ?.sortedByDescending { it.sizeBytes }
                ?: emptyList()
        } else {
            emptyList()
        }

        val directFileSize = dir.listFiles()
            ?.filter { it.isFile }
            ?.sumOf { it.length() }
            ?: 0L

        return FolderNode(
            path = dir.absolutePath,
            name = displayName,
            sizeBytes = directFileSize + children.sumOf { it.sizeBytes },
            children = children
        )
    }
}
