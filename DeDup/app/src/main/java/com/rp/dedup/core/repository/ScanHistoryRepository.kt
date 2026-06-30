package com.rp.dedup.core.repository

import com.rp.dedup.core.model.ScanHistory
import com.rp.dedup.core.dao.ScanHistoryDao
import kotlinx.coroutines.flow.Flow

class ScanHistoryRepository(private val dao: ScanHistoryDao) : IScanHistoryRepository {
    override fun getAll(): Flow<List<ScanHistory>> = dao.getAll()
    override suspend fun insert(scan: ScanHistory) { dao.insert(scan) }
    override suspend fun delete(scan: ScanHistory) = dao.delete(scan)
    override suspend fun clearAll() = dao.clearAll()
}
