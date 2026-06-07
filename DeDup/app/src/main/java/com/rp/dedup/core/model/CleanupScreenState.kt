package com.rp.dedup.core.model

data class CleanupScreenState(
    val videoStats: CleanupCategoryStats = CleanupCategoryStats(),
    val archiveStats: CleanupCategoryStats = CleanupCategoryStats(),
    val appDownloadStats: CleanupCategoryStats = CleanupCategoryStats(),
    val oldDownloadStats: CleanupCategoryStats = CleanupCategoryStats()
)
