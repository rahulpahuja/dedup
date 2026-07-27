package com.rp.dedup.core.repository

import com.rp.dedup.core.dao.TrashDao
import com.rp.dedup.core.model.TrashItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TrashRepository(private val dao: TrashDao) : ITrashRepository {

    override fun getAll(): Flow<List<TrashItem>> = dao.getAll()

    override suspend fun insert(item: TrashItem) = dao.insert(item)

    override suspend fun delete(item: TrashItem) = dao.delete(item)

    override suspend fun deleteExpired(now: Long): List<TrashItem> {
        val expired = dao.getAll().first().filter { it.expiresAtMs < now }
        dao.deleteExpired(now)
        return expired
    }

    override suspend fun clearAll(): List<TrashItem> {
        val all = dao.getAll().first()
        dao.clearAll()
        return all
    }

    override fun getCount(): Flow<Int> = dao.getCount()

    override fun getTotalSize(): Flow<Long?> = dao.getTotalSize()
}
