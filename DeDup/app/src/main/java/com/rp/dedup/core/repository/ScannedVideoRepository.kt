package com.rp.dedup.core.repository

import android.net.Uri
import com.rp.dedup.core.dao.ScannedVideoDao
import com.rp.dedup.core.model.ScannedVideoEntity

class ScannedVideoRepository(private val dao: ScannedVideoDao) : IScannedVideoRepository {

    suspend fun insertVideos(videos: List<ScannedVideoEntity>) = dao.insertVideos(videos)

    suspend fun getScannedUris(): Set<Uri> =
        dao.getScannedUris().map { Uri.parse(it) }.toHashSet()

    suspend fun getCachedDuplicateGroups() =
        dao.getCachedDuplicateVideos()
            .groupBy { it.groupKey }
            .values
            .filter { it.size > 1 }
            .map { group -> group.map { it.toScannedVideo() } }

    suspend fun getTotalScannedCount(): Int = dao.getTotalCount()

    suspend fun deleteByUri(uri: String) = dao.deleteByUri(uri)

    suspend fun clearAll() = dao.clearAll()
}
