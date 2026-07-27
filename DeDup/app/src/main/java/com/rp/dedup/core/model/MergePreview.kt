package com.rp.dedup.core.model

/** All data needed to render the merge-selection dialog for one duplicate group. */
data class MergePreviewGroup(
    val primaryId: String,
    val primaryName: String,
    val duplicateIds: List<String>,
    val phoneEntries: List<ContactDataEntry>,
    val emailEntries: List<ContactDataEntry>
)
