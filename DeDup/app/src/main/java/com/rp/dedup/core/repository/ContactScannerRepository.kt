package com.rp.dedup.core.repository

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import com.rp.dedup.core.model.ContactDataEntry
import com.rp.dedup.core.model.ScannedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ContactScannerRepository(private val context: Context) {

    // ── Scanning ──────────────────────────────────────────────────────────────

    fun scanContacts(): Flow<ScannedContact> = flow {
        val contactsMap = mutableMapOf<String, ScannedContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null, null
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numCol  = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val id     = cursor.getString(idCol)
                val name   = cursor.getString(nameCol) ?: "Unknown"
                val number = cursor.getString(numCol)  ?: ""
                val existing = contactsMap[id]
                if (existing != null) {
                    if (!existing.phoneNumbers.contains(number))
                        contactsMap[id] = existing.copy(phoneNumbers = existing.phoneNumbers + number)
                } else {
                    contactsMap[id] = ScannedContact(id, name, listOf(number), emptyList())
                }
            }
        }
        contactsMap.values.forEach { emit(it) }
    }.flowOn(Dispatchers.IO)

    // ── Merge-preview data ────────────────────────────────────────────────────

    /** Returns all phone Data rows for [contactId] as [ContactDataEntry] objects. */
    suspend fun queryPhoneEntries(
        contactId: String,
        contactName: String,
        isPrimary: Boolean
    ): List<ContactDataEntry> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val entries = mutableListOf<ContactDataEntry>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            ),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(1) ?: return@use
                entries.add(ContactDataEntry(
                    dataId        = cursor.getString(0),
                    value         = number,
                    type          = cursor.getInt(2),
                    contactId     = contactId,
                    contactName   = contactName,
                    isFromPrimary = isPrimary
                ))
            }
        }
        entries
    }

    /** Returns all email Data rows for [contactId] as [ContactDataEntry] objects. */
    suspend fun queryEmailEntries(
        contactId: String,
        contactName: String,
        isPrimary: Boolean
    ): List<ContactDataEntry> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val entries = mutableListOf<ContactDataEntry>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE
            ),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(1) ?: return@use
                entries.add(ContactDataEntry(
                    dataId        = cursor.getString(0),
                    value         = address,
                    type          = cursor.getInt(2),
                    contactId     = contactId,
                    contactName   = contactName,
                    isFromPrimary = isPrimary
                ))
            }
        }
        entries
    }

    // ── Merge with user selection ─────────────────────────────────────────────

    /**
     * Executes the merge for one duplicate group based on explicit user selection:
     *
     * - [primaryDataIdsToRemove]: Data._IDs on the primary that the user unchecked (delete them).
     * - [entriesToAddFromDuplicates]: entries from duplicates the user checked (copy to primary).
     * - [duplicateIds]: raw contacts under these contact IDs are deleted after data is copied.
     *
     * [keepId] is never deleted even if it appears in [duplicateIds] by mistake.
     */
    suspend fun mergeContactsWithSelection(
        keepId: String,
        duplicateIds: List<String>,
        primaryDataIdsToRemove: Set<String>,
        entriesToAddFromDuplicates: List<ContactDataEntry>
    ): Result<Unit> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val idsToDelete = duplicateIds.filter { it != keepId }
            val primaryRawId = queryFirstRawContactId(keepId)
                ?: return@withContext Result.failure(Exception("Primary raw contact not found"))

            val operations = ArrayList<ContentProviderOperation>()

            // 1. Remove unchecked data rows from the primary.
            for (dataId in primaryDataIdsToRemove) {
                operations.add(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(dataId))
                        .build()
                )
            }

            // 2. Add checked entries from duplicates (skip if value already exists on primary
            //    after the removals — check by normalised value to avoid re-adding same number).
            val existingNormalizedPhones = queryPhoneEntries(keepId, "", true)
                .filter { it.dataId !in primaryDataIdsToRemove }
                .map { normalizePhone(it.value) }
                .toMutableSet()
            val existingNormalizedEmails = queryEmailEntries(keepId, "", true)
                .filter { it.dataId !in primaryDataIdsToRemove }
                .map { it.value.lowercase().trim() }
                .toMutableSet()

            for (entry in entriesToAddFromDuplicates) {
                val mimetype: String
                val valueColumn: String
                val typeColumn: String
                val isDuplicate: Boolean

                if (entry.type in PHONE_TYPES || looksLikePhone(entry.value)) {
                    mimetype    = ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    valueColumn = ContactsContract.CommonDataKinds.Phone.NUMBER
                    typeColumn  = ContactsContract.CommonDataKinds.Phone.TYPE
                    isDuplicate = !existingNormalizedPhones.add(normalizePhone(entry.value))
                } else {
                    mimetype    = ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                    valueColumn = ContactsContract.CommonDataKinds.Email.ADDRESS
                    typeColumn  = ContactsContract.CommonDataKinds.Email.TYPE
                    isDuplicate = !existingNormalizedEmails.add(entry.value.lowercase().trim())
                }

                if (!isDuplicate) {
                    operations.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, primaryRawId)
                            .withValue(ContactsContract.Data.MIMETYPE, mimetype)
                            .withValue(valueColumn, entry.value)
                            .withValue(typeColumn, entry.type)
                            .build()
                    )
                }
            }

            // 3. Delete the raw contacts for each duplicate contact ID.
            if (idsToDelete.isNotEmpty()) {
                val placeholders = idsToDelete.joinToString(",") { "?" }
                context.contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(ContactsContract.RawContacts._ID),
                    "${ContactsContract.RawContacts.CONTACT_ID} IN ($placeholders)",
                    idsToDelete.toTypedArray(),
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val rawId = cursor.getString(0)
                        operations.add(
                            ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                                .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawId))
                                .build()
                        )
                    }
                }
            }

            if (operations.isNotEmpty()) {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            }

            android.util.Log.d("ContactScannerRepo",
                "Merged: removed ${primaryDataIdsToRemove.size} rows from primary, " +
                "added ${entriesToAddFromDuplicates.size} entries, deleted contacts $idsToDelete")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ContactScannerRepo", "Failed to merge contacts", e)
            Result.failure(e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun queryFirstRawContactId(contactId: String): String? =
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId),
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun normalizePhone(phone: String): String =
        phone.replace("[^0-9+]".toRegex(), "").let {
            if (it.startsWith("+")) it else it.trimStart('0')
        }

    private fun looksLikePhone(value: String) =
        value.replace("[^0-9]".toRegex(), "").length >= 7

    companion object {
        private val PHONE_TYPES = setOf(
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME,
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER,
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN,
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME,
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK
        )
    }
}
