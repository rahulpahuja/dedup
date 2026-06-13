package com.rp.dedup.core.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rp.dedup.core.model.ScannedVideoEntity

@Dao
interface ScannedVideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<ScannedVideoEntity>)

    /** All URIs that have been processed — used to skip them on resume. */
    @Query("SELECT uri FROM scanned_videos")
    suspend fun getScannedUris(): List<String>

    /** Only videos that are part of a duplicate group — used to restore results UI. */
    @Query("SELECT * FROM scanned_videos WHERE groupKey != ''")
    suspend fun getCachedDuplicateVideos(): List<ScannedVideoEntity>

    @Query("SELECT COUNT(*) FROM scanned_videos")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM scanned_videos WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM scanned_videos")
    suspend fun clearAll()
}
