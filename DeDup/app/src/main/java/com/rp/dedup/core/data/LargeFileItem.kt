package com.rp.dedup.core.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class LargeFileItem(
    val title: String,
    val subtitle: String,
    val sizeBytes: Long,      // used for filtering
    val sizeLabel: String,    // displayed value (may be a count or size string)
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val isCountType: Boolean = false
)