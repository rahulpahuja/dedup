package com.rp.dedup.core.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rp.dedup.core.model.TrashItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY trashedAtMs DESC")
    fun getAll(): Flow<List<TrashItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TrashItem)

    @Delete
    suspend fun delete(item: TrashItem)

    @Query("DELETE FROM trash_items WHERE expiresAtMs < :now")
    suspend fun deleteExpired(now: Long)

    @Query("DELETE FROM trash_items")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM trash_items")
    fun getCount(): Flow<Int>

    @Query("SELECT SUM(size) FROM trash_items")
    fun getTotalSize(): Flow<Long?>
}
