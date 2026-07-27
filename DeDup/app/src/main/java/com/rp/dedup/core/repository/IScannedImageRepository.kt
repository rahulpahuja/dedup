package com.rp.dedup.core.repository

import com.rp.dedup.core.model.ScannedImage
import kotlinx.coroutines.flow.Flow

interface IScannedImageRepository {
    fun getAllImages(): Flow<List<ScannedImage>>
    suspend fun getCachedDuplicateGroups(): List<List<ScannedImage>>
    suspend fun insertImages(images: List<ScannedImage>)
    suspend fun deleteByUri(uri: String)
    suspend fun clearAll()
}
