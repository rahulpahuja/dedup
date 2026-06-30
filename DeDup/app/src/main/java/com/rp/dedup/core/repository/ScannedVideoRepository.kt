package com.rp.dedup.core.repository

import android.net.Uri
import com.rp.dedup.core.dao.ScannedVideoDao
import com.rp.dedup.core.model.ScannedVideoEntity

class ScannedVideoRepository(private val dao: ScannedVideoDao) : IScannedVideoRepository {

    override suspend fun insertVideos(videos: List<ScannedVideoEntity>) = dao.insertVideos(videos)

    override suspend fun getScannedUris(): Set<Uri> =
        dao.getScannedUris().map { Uri.parse(it) }.toHashSet()

    override suspend fun getCachedDuplicateGroups() =
        dao.getCachedDuplicateVideos()
            .groupBy { it.groupKey }
            .values
            .filter { it.size > 1 }
            .map { group -> group.map { it.toScannedVideo() } }

    override suspend fun getTotalScannedCount(): Int = dao.getTotalCount()

    override suspend fun deleteByUri(uri: String) = dao.deleteByUri(uri)

    override suspend fun clearAll() = dao.clearAll()
}
