package com.rp.dedup.core.repository

import com.rp.dedup.core.dao.ScannedImageDao
import com.rp.dedup.core.model.ScannedImage
import kotlinx.coroutines.flow.Flow

class ScannedImageRepository(private val dao: ScannedImageDao) : IScannedImageRepository {
    override fun getAllImages(): Flow<List<ScannedImage>> = dao.getAllImages()

    override suspend fun getCachedDuplicateGroups(): List<List<ScannedImage>> =
        dao.getCachedDuplicateImages()
            .groupBy { it.groupKey }
            .values
            .filter { it.size > 1 }
            .toList()

    override suspend fun insertImages(images: List<ScannedImage>) = dao.insertImages(images)
    override suspend fun deleteByUri(uri: String) = dao.deleteByUri(uri)
    override suspend fun clearAll() = dao.clearAll()
}
