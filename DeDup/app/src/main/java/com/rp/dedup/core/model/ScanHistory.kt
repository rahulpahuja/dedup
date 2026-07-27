package com.rp.dedup.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanType: String,
    val timestamp: Long,
    val durationMs: Long,
    val totalScanned: Int,
    val duplicateGroups: Int,
    val totalDuplicates: Int,
    val reclaimableBytes: Long,
    val status: String
)
