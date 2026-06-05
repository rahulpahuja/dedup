package com.rp.dedup.core.repository

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import com.rp.dedup.core.model.ScannedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ContactScannerRepository(private val context: Context) {

    fun scanContacts(): Flow<ScannedContact> = flow {
        // Android 17 standardized picker handling (simplified logic)
        // In API 37, we transition to using ACTION_PICK_CONTACTS for privacy
        // but for deep scanning/deduping we still need the full list if permitted.

        val contactsMap = mutableMapOf<String, ScannedContact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null, null, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val number = cursor.getString(numCol) ?: ""

                val existing = contactsMap[id]
                if (existing != null) {
                    if (!existing.phoneNumbers.contains(number)) {
                        contactsMap[id] = existing.copy(phoneNumbers = existing.phoneNumbers + number)
                    }
                } else {
                    contactsMap[id] = ScannedContact(id, name, listOf(number), emptyList())
                }
            }
        }
        
        contactsMap.values.forEach { emit(it) }
    }.flowOn(Dispatchers.IO)

    suspend fun mergeContacts(idsToMerge: List<String>): Result<Unit> = with(Dispatchers.IO) {
        try {
            // Simple merging strategy: delete the duplicates.
            // A more advanced strategy would be to combine their info into one primary record.
            val operations = idsToMerge.map { id ->
                android.content.ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                    .withSelection("${ContactsContract.RawContacts.CONTACT_ID} = ?", arrayOf(id))
                    .build()
            }

            if (operations.isNotEmpty()) {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(operations))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
