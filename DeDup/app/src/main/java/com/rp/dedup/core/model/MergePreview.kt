package com.rp.dedup.core.model

/** A single phone/email row from the Contacts Data table. */
data class ContactDataEntry(
    val dataId: String,          // Data._ID — used to delete the row precisely
    val value: String,           // phone number or email address
    val type: Int,               // Phone.TYPE_MOBILE, Email.TYPE_WORK, etc.
    val contactId: String,
    val contactName: String,
    val isFromPrimary: Boolean
)

/** All data needed to render the merge-selection dialog for one duplicate group. */
data class MergePreviewGroup(
    val primaryId: String,
    val primaryName: String,
    val duplicateIds: List<String>,
    val phoneEntries: List<ContactDataEntry>,
    val emailEntries: List<ContactDataEntry>
)
