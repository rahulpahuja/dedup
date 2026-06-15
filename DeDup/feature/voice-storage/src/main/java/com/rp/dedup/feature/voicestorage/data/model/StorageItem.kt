package com.rp.dedup.feature.voicestorage.data.model

import android.net.Uri

enum class MediaType { IMAGE, VIDEO, AUDIO, DOCUMENT }

data class StorageItem(
    val uri: Uri,
    val displayName: String,
    val sizeInBytes: Long,
    val dateAdded: Long,
    val mimeType: String,
    val mediaType: MediaType,
)
