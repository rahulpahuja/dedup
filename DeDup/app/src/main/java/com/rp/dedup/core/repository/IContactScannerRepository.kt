package com.rp.dedup.core.repository

import com.rp.dedup.core.model.ContactDataEntry
import com.rp.dedup.core.model.ScannedContact
import kotlinx.coroutines.flow.Flow

interface IContactScannerRepository {
    fun scanContacts(): Flow<ScannedContact>
    suspend fun queryPhoneEntries(
        contactId: String,
        contactName: String,
        isPrimary: Boolean
    ): List<ContactDataEntry>
    suspend fun queryEmailEntries(
        contactId: String,
        contactName: String,
        isPrimary: Boolean
    ): List<ContactDataEntry>
    suspend fun mergeContactsWithSelection(
        keepId: String,
        duplicateIds: List<String>,
        primaryDataIdsToRemove: Set<String>,
        entriesToAddFromDuplicates: List<ContactDataEntry>
    ): Result<Unit>
    fun normalizePhone(phone: String): String
}
