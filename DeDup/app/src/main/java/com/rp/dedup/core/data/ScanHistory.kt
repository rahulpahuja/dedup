package com.rp.dedup.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanType: String,         // "IMAGE", "VIDEO", "FILE_PDF", "FILE_APK"
    val timestamp: Long,          // epoch millis when scan started
    val durationMs: Long,         // how long the scan took
    val totalScanned: Int,        // total files processed
    val duplicateGroups: Int,     // number of duplicate groups found
    val totalDuplicates: Int,     // total duplicate files (excludes the "keeper" in each group)
    val reclaimableBytes: Long,   // bytes that could be freed by deleting duplicates
    val status: String            // "COMPLETED" or "CANCELLED"
)