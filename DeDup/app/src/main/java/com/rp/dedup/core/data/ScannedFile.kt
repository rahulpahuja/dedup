package com.rp.dedup.core.data

import android.net.Uri

data class ScannedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val path: String,
    val extension: String
)
