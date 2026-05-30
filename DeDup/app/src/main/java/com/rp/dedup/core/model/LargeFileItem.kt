package com.rp.dedup.core.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class LargeFileItem(
    val title: String,
    val subtitle: String,
    val sizeBytes: Long,
    val sizeLabel: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val isCountType: Boolean = false
)
