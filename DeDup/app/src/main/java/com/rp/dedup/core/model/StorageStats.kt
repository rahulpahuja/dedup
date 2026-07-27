package com.rp.dedup.core.model

data class StorageStats(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L
) {
    val usedFraction: Float
        get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}
