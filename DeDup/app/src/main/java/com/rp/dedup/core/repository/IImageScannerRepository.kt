package com.rp.dedup.core.repository

import com.rp.dedup.core.model.ScannedImage
import kotlinx.coroutines.flow.Flow

interface IImageScannerRepository {
    fun scanImagesInParallel(
        concurrencyLevel: Int = 8,
        excludedFolders: List<String> = emptyList()
    ): Flow<ScannedImage>
}
