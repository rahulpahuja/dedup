package com.rp.dedup.core.model

import java.io.File

data class StorageVolume(
    val name: String,
    val file: File,
    val isPrimary: Boolean,
    val totalBytes: Long,
    val freeBytes: Long
)
