package com.rp.dedup.core

import android.net.Uri

data class ScannedImage(
    val uri: Uri,
    val dHash: Long,
    val sizeInBytes: Long // <-- NEW FIELD
)