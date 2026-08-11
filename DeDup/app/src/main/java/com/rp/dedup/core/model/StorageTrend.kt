package com.rp.dedup.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "storage_trends")
data class StorageTrend(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val freeBytes: Long
)
