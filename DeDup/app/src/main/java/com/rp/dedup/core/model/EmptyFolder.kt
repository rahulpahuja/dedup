package com.rp.dedup.core.model

data class EmptyFolder(
    val path: String,
    val name: String,
    val parentPath: String,
    // Non-null when the folder was discovered via SAF (Android 11+); null for File API path.
    val documentUri: String? = null
)
