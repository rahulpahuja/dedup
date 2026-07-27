package com.rp.dedup.core.deepoptimization

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.rp.dedup.core.model.FolderNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface StorageTreeRepository {
    suspend fun buildTree(maxDepth: Int = 100): FolderNode
}

class StorageTreeRepositoryImpl(private val context: Context) : StorageTreeRepository {

    private val externalRoot = Environment.getExternalStorageDirectory()

    override suspend fun buildTree(maxDepth: Int): FolderNode = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            buildTreeFromMediaStore()
        } else {
            buildTreeFromFiles(maxDepth)
        }
    }

    // Android 11+: build tree from MediaStore — no MANAGE_EXTERNAL_STORAGE needed.
    // Covers all user-accessible media and documents. System/app-private data appears
    // in the "System & Restricted Data" remainder node (calculated via StatFs).
    private fun buildTreeFromMediaStore(): FolderNode {
        // Accumulator node: holds total size and named children
        class Acc(var size: Long = 0, val children: LinkedHashMap<String, Acc> = LinkedHashMap())

        val root = Acc()

        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns.RELATIVE_PATH, MediaStore.Files.FileColumns.SIZE),
            "${MediaStore.Files.FileColumns.SIZE} > 0",
            null, null
        )?.use { cursor ->
            val relPathIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val sizeIdx    = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            while (cursor.moveToNext()) {
                val relPath = cursor.getString(relPathIdx)?.trimEnd('/') ?: continue
                val size    = cursor.getLong(sizeIdx)
                val parts   = relPath.split('/').filter { it.isNotEmpty() }
                root.size += size
                var node = root
                for (part in parts) {
                    val child = node.children.getOrPut(part) { Acc() }
                    child.size += size
                    node = child
                }
            }
        }

        fun Acc.toFolderNode(name: String, path: String): FolderNode = FolderNode(
            path      = path,
            name      = name,
            sizeBytes = size,
            children  = children.entries
                .map { (n, a) -> a.toFolderNode(n, "$path/$n") }
                .sortedByDescending { it.sizeBytes }
        )

        val stat        = StatFs(externalRoot.path)
        val totalUsed   = (stat.blockCountLong - stat.availableBlocksLong) * stat.blockSizeLong
        val systemOther = totalUsed - root.size

        val topChildren = root.children.entries
            .map { (name, acc) -> acc.toFolderNode(name, name) }
            .sortedByDescending { it.sizeBytes }
            .toMutableList()

        if (systemOther > 0) {
            topChildren += FolderNode("system_other", "System & Restricted Data", systemOther, emptyList())
            topChildren.sortByDescending { it.sizeBytes }
        }

        return FolderNode(
            path      = "storage_root",
            name      = "Internal Storage",
            sizeBytes = totalUsed,
            children  = topChildren
        )
    }

    // Android < 11: File API works with READ_EXTERNAL_STORAGE (no MANAGE needed on these versions).
    private fun buildTreeFromFiles(maxDepth: Int): FolderNode {
        val rootNode = buildNode(externalRoot, currentDepth = 0, maxDepth = maxDepth)

        val stat             = StatFs(externalRoot.path)
        val totalUsedOnDevice = (stat.blockCountLong - stat.availableBlocksLong) * stat.blockSizeLong
        val difference       = totalUsedOnDevice - rootNode.sizeBytes

        return if (difference > 0) {
            val systemNode = FolderNode(
                path      = "system_other",
                name      = "System & Restricted Data",
                sizeBytes = difference,
                children  = emptyList()
            )
            rootNode.copy(
                sizeBytes = totalUsedOnDevice,
                children  = (rootNode.children + systemNode).sortedByDescending { it.sizeBytes }
            )
        } else {
            rootNode
        }
    }

    private fun buildNode(dir: File, currentDepth: Int, maxDepth: Int): FolderNode {
        val displayName = if (dir == externalRoot) "Internal Storage" else dir.name
        val files       = dir.listFiles() ?: emptyArray()

        var totalSize      = 0L
        val childrenNodes  = mutableListOf<FolderNode>()

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
            path      = dir.absolutePath,
            name      = displayName,
            sizeBytes = totalSize,
            children  = childrenNodes.sortedByDescending { it.sizeBytes }
        )
    }

    private fun calculateTotalSizeRecursive(dir: File): Long {
        var size  = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) calculateTotalSizeRecursive(file) else file.length()
        }
        return size
    }
}
