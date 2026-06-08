package com.rp.dedup.core.model.state

import com.rp.dedup.core.model.CleanupCategoryStats

data class CleanupScreenState(
    val videoStats: CleanupCategoryStats = CleanupCategoryStats(),
    val archiveStats: CleanupCategoryStats = CleanupCategoryStats(),
    val appDownloadStats: CleanupCategoryStats = CleanupCategoryStats(),
    val oldDownloadStats: CleanupCategoryStats = CleanupCategoryStats()
)
