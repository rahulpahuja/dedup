package com.rp.dedup.core.viewmodels

data class CleanupScreenState(
    val videoStats: CleanupCategoryStats = CleanupCategoryStats(),
    val archiveStats: CleanupCategoryStats = CleanupCategoryStats(),
    val appDownloadStats: CleanupCategoryStats = CleanupCategoryStats()
)