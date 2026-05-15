package com.rp.dedup.core.repository

import com.rp.dedup.core.dao.ScannedImageDao
import com.rp.dedup.core.data.ScannedImage
import kotlinx.coroutines.flow.Flow

class ScannedImageRepository(private val dao: ScannedImageDao) {
    fun getAllImages(): Flow<List<ScannedImage>> = dao.getAllImages()
    suspend fun insertImages(images: List<ScannedImage>) = dao.insertImages(images)
    suspend fun deleteByUri(uri: String) = dao.deleteByUri(uri)
    suspend fun clearAll() = dao.clearAll()
}
