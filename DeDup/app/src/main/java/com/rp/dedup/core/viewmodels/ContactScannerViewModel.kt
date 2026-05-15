package com.rp.dedup.core.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.data.ScannedContact
import com.rp.dedup.core.repository.ContactScannerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactScannerViewModel(private val repository: ContactScannerRepository) : ViewModel() {

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedContact>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedContact>>> = _duplicateGroups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startScanning() {
        _isScanning.value = true
        viewModelScope.launch {
            val allContacts = mutableListOf<ScannedContact>()
            repository.scanContacts().collect { allContacts.add(it) }
            
            findDuplicates(allContacts)
            _isScanning.value = false
        }
    }

    private fun findDuplicates(contacts: List<ScannedContact>) {
        // Group by name first
        val nameGroups = contacts.groupBy { it.name }.filter { it.value.size > 1 }
        
        // Group by normalized phone numbers
        val phoneMap = mutableMapOf<String, MutableList<ScannedContact>>()
        contacts.forEach { contact ->
            contact.phoneNumbers.forEach { num ->
                val normalized = num.replace("[^0-9]".toRegex(), "")
                if (normalized.length >= 10) {
                    phoneMap.getOrPut(normalized) { mutableListOf() }.add(contact)
                }
            }
        }
        val phoneGroups = phoneMap.filter { it.value.size > 1 }.values
        
        _duplicateGroups.value = (nameGroups.values + phoneGroups).distinctBy { group ->
            group.map { it.id }.sorted()
        }
    }
}
