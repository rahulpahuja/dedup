package com.rp.dedup.core.browser

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,           // bytes; 0 for directories
    val lastModified: Long,   // epoch millis
    val extension: String,    // lowercase, empty for dirs
    val childCount: Int = 0   // populated for directories
)
