package com.rp.dedup.core.repository

import com.rp.dedup.core.dao.StorageTrendDao
import com.rp.dedup.core.model.ForecastConfidence
import com.rp.dedup.core.model.StorageForecast
import com.rp.dedup.core.model.StorageTrend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.concurrent.TimeUnit

class StorageForecastingRepository(private val dao: StorageTrendDao) {

    val forecast: Flow<StorageForecast?> = dao.getAll().map { trends ->
        calculateForecast(trends)
    }

    /**
     * Records a new storage snapshot if at least 24 hours have passed
     * since the last recorded snapshot.
     */
    suspend fun recordSnapshotIfNecessary(freeBytes: Long) {
        val latest = dao.getLatest()
        val now = System.currentTimeMillis()
        
        if (latest == null || (now - latest.timestamp) >= TimeUnit.DAYS.toMillis(1)) {
            dao.insert(StorageTrend(timestamp = now, freeBytes = freeBytes))
        }
    }

    private fun calculateForecast(trends: List<StorageTrend>): StorageForecast? {
        if (trends.size < 2) return null

        // Trends are ordered by timestamp DESC in Dao
        val sortedTrends = trends.sortedBy { it.timestamp }
        
        // Simple velocity calculation: (Total Free Byte change) / (Total Time elapsed)
        val first = sortedTrends.first()
        val last = sortedTrends.last()
        
        val timeElapsedMs = last.timestamp - first.timestamp
        if (timeElapsedMs <= 0) return null
        
        val bytesConsumed = first.freeBytes - last.freeBytes
        
        // If user is actually gaining space, we can't project a "Full" date easily
        if (bytesConsumed <= 0) return null
        
        val velocityPerMs = bytesConsumed.toDouble() / timeElapsedMs
        val velocityPerDay = (velocityPerMs * TimeUnit.DAYS.toMillis(1)).toLong()
        
        val currentFreeBytes = last.freeBytes
        val msRemaining = (currentFreeBytes / velocityPerMs).toLong()
        val daysRemaining = TimeUnit.MILLISECONDS.toDays(msRemaining).toInt()
        
        val estimatedFullDate = Date(last.timestamp + msRemaining)
        
        val confidence = when {
            trends.size >= 7 -> ForecastConfidence.HIGH
            trends.size >= 3 -> ForecastConfidence.MEDIUM
            else -> ForecastConfidence.LOW
        }

        return StorageForecast(
            daysRemaining = daysRemaining,
            estimatedFullDate = estimatedFullDate,
            dailyUsageVelocity = velocityPerDay,
            confidence = confidence
        )
    }
}
