package com.rp.dedup.core.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.rp.dedup.core.model.StorageVolume
import java.io.File

object StorageUtils {

    fun getAllStorageVolumes(context: Context): List<StorageVolume> {
        val volumes = mutableListOf<StorageVolume>()
        
        // 1. Primary Storage
        val primaryRoot = Environment.getExternalStorageDirectory()
        volumes.add(createVolume(primaryRoot, "Internal Storage", true))

        // 2. Additional Volumes (SD Cards, USB)
        // getExternalFilesDirs returns [/storage/emulated/0/Android/data/..., /storage/XXXX-XXXX/Android/data/...]
        val externalFilesDirs = context.getExternalFilesDirs(null)
        externalFilesDirs.forEach { dir ->
            if (dir != null) {
                val root = findRootPath(dir)
                if (root != null && root.absolutePath != primaryRoot.absolutePath) {
                    // Check if we already added this (some devices might list multiple subpaths)
                    if (volumes.none { it.file.absolutePath == root.absolutePath }) {
                        volumes.add(createVolume(root, "SD Card", false))
                    }
                }
            }
        }

        return volumes
    }

    private fun createVolume(root: File, defaultName: String, isPrimary: Boolean): StorageVolume {
        return try {
            val stat = StatFs(root.path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            StorageVolume(
                name = if (isPrimary) "Internal Storage" else root.name,
                file = root,
                isPrimary = isPrimary,
                totalBytes = total,
                freeBytes = free
            )
        } catch (e: Exception) {
            StorageVolume(defaultName, root, isPrimary, 0L, 0L)
        }
    }

    /**
     * Extracts the mount point root from an app-specific path.
     * e.g., /storage/A1B2-C3D4/Android/data/com.rp.dedup/files -> /storage/A1B2-C3D4
     */
    private fun findRootPath(file: File): File? {
        var current = file
        while (current.parentFile != null && current.parentFile!!.name != "storage" && current.parentFile!!.name != "mnt") {
            current = current.parentFile!!
        }
        // If we reached /storage/XXXX or /mnt/media_rw/XXXX, current is the root
        return if (current.exists() && current.canRead()) current else null
    }
}
