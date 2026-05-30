package com.rp.dedup.core.model

data class CleanupCategoryStats(
    val totalSize: Long = 0L,
    val count: Int = 0,
    val isLoading: Boolean = false,
    val files: List<ScannedFile> = emptyList()
)
