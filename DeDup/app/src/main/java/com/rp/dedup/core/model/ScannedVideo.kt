package com.rp.dedup.core.model

import android.net.Uri

data class ScannedVideo(
    val uri: Uri,
    val name: String,
    val sizeInBytes: Long,
    val durationMs: Long,
    val mimeType: String,
    val frameHashes: List<Long> = emptyList()
)
