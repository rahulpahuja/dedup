package com.rp.dedup.core.deepoptimization

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.rp.dedup.core.data.FolderNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface StorageTreeRepository {
    suspend fun buildTree(maxDepth: Int = 100): FolderNode
}

class StorageTreeRepositoryImpl(private val context: Context) : StorageTreeRepository {

    private val externalRoot = Environment.getExternalStorageDirectory()

    override suspend fun buildTree(maxDepth: Int): FolderNode = withContext(Dispatchers.IO) {
        val rootNode = buildNode(externalRoot, currentDepth = 0, maxDepth = maxDepth)
        
        // Add "System & Other" node to account for the difference between StatFs and File Scan
        val stat = StatFs(externalRoot.path)
        val totalUsedOnDevice = (stat.blockCountLong - stat.availableBlocksLong) * stat.blockSizeLong
        val difference = totalUsedOnDevice - rootNode.sizeBytes
        
        if (difference > 0) {
            val systemNode = FolderNode(
                path = "system_other",
                name = "System & Restricted Data",
                sizeBytes = difference,
                children = emptyList()
            )
            rootNode.copy(
                sizeBytes = totalUsedOnDevice,
                children = (rootNode.children + systemNode).sortedByDescending { it.sizeBytes }
            )
        } else {
            rootNode
        }
    }

    private fun buildNode(dir: File, currentDepth: Int, maxDepth: Int): FolderNode {
        val displayName = if (dir == externalRoot) "Internal Storage" else dir.name
        val files = dir.listFiles() ?: emptyArray()
        
        var totalSize = 0L
        val childrenNodes = mutableListOf<FolderNode>()

        for (file in files) {
            if (file.name.startsWith(".")) continue
            
            if (file.isDirectory) {
                if (currentDepth < maxDepth) {
                    val childNode = buildNode(file, currentDepth + 1, maxDepth)
                    totalSize += childNode.sizeBytes
                    childrenNodes.add(childNode)
                } else {
                    totalSize += calculateTotalSizeRecursive(file)
                }
            } else {
                totalSize += file.length()
            }
        }

        return FolderNode(
            path = dir.absolutePath,
            name = displayName,
            sizeBytes = totalSize,
            children = childrenNodes.sortedByDescending { it.sizeBytes }
        )
    }

    private fun calculateTotalSizeRecursive(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) {
                calculateTotalSizeRecursive(file)
            } else {
                file.length()
            }
        }
        return size
    }
}
