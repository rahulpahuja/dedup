package com.rp.dedup.feature.voicestorage.domain

import com.rp.dedup.feature.voicestorage.data.model.MediaType

enum class SortBy { DATE_ADDED, SIZE, NAME }
enum class SortOrder { ASC, DESC }

data class FilterConfig(
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val dateAddedAfter: Long? = null,
    val dateAddedBefore: Long? = null,
    val mediaTypes: Set<MediaType> = setOf(MediaType.IMAGE, MediaType.VIDEO),
    val sortBy: SortBy = SortBy.DATE_ADDED,
    val sortOrder: SortOrder = SortOrder.DESC,
    // Specific MIME type from an extension mention, e.g. "video/mp4", "application/pdf"
    val mimeTypeFilter: String? = null,
)
