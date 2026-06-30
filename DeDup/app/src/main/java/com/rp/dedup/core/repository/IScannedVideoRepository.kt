package com.rp.dedup.core.repository

import android.net.Uri
import com.rp.dedup.core.model.ScannedVideo
import com.rp.dedup.core.model.ScannedVideoEntity

interface IScannedVideoRepository {
    suspend fun insertVideos(videos: List<ScannedVideoEntity>)
    suspend fun getScannedUris(): Set<Uri>
    suspend fun getCachedDuplicateGroups(): List<List<ScannedVideo>>
    suspend fun getTotalScannedCount(): Int
    suspend fun deleteByUri(uri: String)
    suspend fun clearAll()
}
