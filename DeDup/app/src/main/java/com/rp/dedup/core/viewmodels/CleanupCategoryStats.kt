package com.rp.dedup.core.viewmodels

import com.rp.dedup.core.data.ScannedFile

data class CleanupCategoryStats(
    val totalSize: Long = 0L,
    val count: Int = 0,
    val isLoading: Boolean = false,
    val files: List<ScannedFile> = emptyList()
)