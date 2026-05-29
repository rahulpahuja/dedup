package com.rp.dedup.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_images")
data class ScannedImage(
    @PrimaryKey val uri: String,
    val dHash: Long,
    val sizeInBytes: Long,
    val dateModified: Long = 0L,
    val qualityScore: Float = 0f,
    val isAiSuggestion: Boolean = false,
    // CRC32 of first 64 KB of file; -1 = not computed. Used for O(1) exact-duplicate detection.
    val exactHash: Long = -1L
)
