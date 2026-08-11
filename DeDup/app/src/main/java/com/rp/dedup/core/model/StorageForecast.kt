package com.rp.dedup.core.model

import java.util.Date

data class StorageForecast(
    val daysRemaining: Int,
    val estimatedFullDate: Date,
    val dailyUsageVelocity: Long, // Bytes per day
    val confidence: ForecastConfidence
)

enum class ForecastConfidence {
    LOW,    // < 3 snapshots
    MEDIUM, // 3-7 snapshots
    HIGH    // > 7 snapshots
}
