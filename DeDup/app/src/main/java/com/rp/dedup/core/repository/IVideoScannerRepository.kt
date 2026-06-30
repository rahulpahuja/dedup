package com.rp.dedup.core.repository

import com.rp.dedup.core.model.ScannedVideo
import kotlinx.coroutines.flow.Flow

interface IVideoScannerRepository {
    fun scanVideos(deepScan: Boolean = false): Flow<ScannedVideo>
}
