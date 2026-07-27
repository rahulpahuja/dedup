package com.rp.dedup.core.repository

import com.rp.dedup.core.model.ScanHistory
import kotlinx.coroutines.flow.Flow

interface IScanHistoryRepository {
    fun getAll(): Flow<List<ScanHistory>>
    suspend fun insert(scan: ScanHistory)
    suspend fun delete(scan: ScanHistory)
    suspend fun clearAll()
}
