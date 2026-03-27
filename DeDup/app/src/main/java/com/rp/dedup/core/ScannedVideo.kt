package com.rp.dedup.core

import android.net.Uri

data class ScannedVideo(
    val uri: Uri,
    val name: String,
    val sizeInBytes: Long,
    val durationMs: Long,
    val mimeType: String
)
