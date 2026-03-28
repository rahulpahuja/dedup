package com.rp.dedup.core.image

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedImageDao {
    @Query("SELECT * FROM scanned_images")
    fun getAllImages(): Flow<List<ScannedImage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ScannedImage>)

    @Query("DELETE FROM scanned_images WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM scanned_images")
    suspend fun clearAll()
}
