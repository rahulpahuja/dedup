package com.rp.dedup.core.repository

import com.rp.dedup.core.model.TrashItem
import kotlinx.coroutines.flow.Flow

interface ITrashRepository {
    fun getAll(): Flow<List<TrashItem>>
    suspend fun insert(item: TrashItem)
    suspend fun delete(item: TrashItem)
    suspend fun deleteExpired(now: Long): List<TrashItem>
    suspend fun clearAll(): List<TrashItem>
    fun getCount(): Flow<Int>
    fun getTotalSize(): Flow<Long?>
}
