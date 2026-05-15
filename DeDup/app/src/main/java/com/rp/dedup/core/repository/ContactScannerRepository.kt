package com.rp.dedup.core.repository

import android.content.Context
import android.provider.ContactsContract
import com.rp.dedup.core.data.ScannedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ContactScannerRepository(private val context: Context) {

    fun scanContacts(): Flow<ScannedContact> = flow {
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
}
