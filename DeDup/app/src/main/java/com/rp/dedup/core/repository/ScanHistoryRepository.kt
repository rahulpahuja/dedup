package com.rp.dedup.core.repository

import com.rp.dedup.core.data.ScanHistory
import com.rp.dedup.core.dao.ScanHistoryDao
import kotlinx.coroutines.flow.Flow

class ScanHistoryRepository(private val dao: ScanHistoryDao) {
    fun getAll(): Flow<List<ScanHistory>> = dao.getAll()
    suspend fun insert(scan: ScanHistory) = dao.insert(scan)
    suspend fun delete(scan: ScanHistory) = dao.delete(scan)
    suspend fun clearAll() = dao.clearAll()
}

class ScanHistoryRepository(private val dao: ScanHistoryDao) {
    fun getAll(): Flow<List<ScanHistory>> = dao.getAll()
    suspend fun insert(scan: ScanHistory) = dao.insert(scan)
    suspend fun delete(scan: ScanHistory) = dao.delete(scan)
    suspend fun clearAll() = dao.clearAll()
}