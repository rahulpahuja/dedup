package com.rp.dedup.core.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.rp.dedup.core.model.StorageTrend
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageTrendDao {
    @Query("SELECT * FROM storage_trends ORDER BY timestamp DESC")
    fun getAll(): Flow<List<StorageTrend>>

    @Insert
    suspend fun insert(trend: StorageTrend)

    @Query("SELECT * FROM storage_trends ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): StorageTrend?

    @Query("DELETE FROM storage_trends")
    suspend fun clearAll()
}
