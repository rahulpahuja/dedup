package com.rp.dedup.core.repository

import com.rp.dedup.core.dao.ScannedImageDao
import com.rp.dedup.core.model.ScannedImage
import kotlinx.coroutines.flow.Flow

class ScannedImageRepository(private val dao: ScannedImageDao) {
    fun getAllImages(): Flow<List<ScannedImage>> = dao.getAllImages()

    /** Loads persisted duplicate groups from Room. Groups by groupKey; filters singletons. */
    suspend fun getCachedDuplicateGroups(): List<List<ScannedImage>> =
        dao.getCachedDuplicateImages()
            .groupBy { it.groupKey }
            .values
            .filter { it.size > 1 }
            .toList()

    suspend fun insertImages(images: List<ScannedImage>) = dao.insertImages(images)
    suspend fun deleteByUri(uri: String) = dao.deleteByUri(uri)
    suspend fun clearAll() = dao.clearAll()
}
