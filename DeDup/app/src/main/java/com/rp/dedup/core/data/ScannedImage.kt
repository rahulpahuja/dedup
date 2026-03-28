package com.rp.dedup.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_images")
data class ScannedImage(
    @PrimaryKey val uri: String,
    val dHash: Long,
    val sizeInBytes: Long
)