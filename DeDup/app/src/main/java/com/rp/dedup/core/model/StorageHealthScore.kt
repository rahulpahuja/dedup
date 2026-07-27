package com.rp.dedup.core.model

data class StorageHealthScore(
    val overallScore: Int,       // 0-100
    val freeSpaceScore: Int,     // 0-30
    val reclaimableScore: Int,   // 0-25
    val scanRecencyScore: Int,   // 0-25
    val cacheSizeScore: Int,     // 0-20
    val label: String,           // "Critical" | "Poor" | "Fair" | "Good" | "Excellent"
    val previousScore: Int? = null
) {
    companion object {
        fun empty() = StorageHealthScore(0, 0, 0, 0, 0, "–")

        fun labelFor(score: Int) = when {
            score >= 85 -> "Excellent"
            score >= 70 -> "Good"
            score >= 50 -> "Fair"
            score >= 30 -> "Poor"
            else        -> "Critical"
        }
    }
}
