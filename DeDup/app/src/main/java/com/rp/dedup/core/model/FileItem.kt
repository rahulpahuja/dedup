package com.rp.dedup.core.model

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String,
    val childCount: Int = 0
)
