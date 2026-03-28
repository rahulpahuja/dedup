package com.rp.dedup.core.scanhistory

import kotlinx.coroutines.flow.Flow

class ScanHistoryRepository(private val dao: ScanHistoryDao) {
    fun getAll(): Flow<List<ScanHistory>> = dao.getAll()
    suspend fun insert(scan: ScanHistory) = dao.insert(scan)
    suspend fun delete(scan: ScanHistory) = dao.delete(scan)
    suspend fun clearAll() = dao.clearAll()
}
