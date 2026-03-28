package com.rp.dedup.core.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.rp.dedup.core.data.ScanHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ScanHistory>>

    @Insert
    suspend fun insert(scan: ScanHistory): Long

    @Delete
    suspend fun delete(scan: ScanHistory)

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}