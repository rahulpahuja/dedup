package com.rp.dedup.core.model

data class WidgetStorageStats(
    val usedLabel: String,
    val freeLabel: String,
    val usedPercent: Int,
    val usedFraction: Float
)
