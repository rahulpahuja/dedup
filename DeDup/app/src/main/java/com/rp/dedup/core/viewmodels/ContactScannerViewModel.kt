package com.rp.dedup.core.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.model.ContactDataEntry
import com.rp.dedup.core.model.MergePreviewGroup
import com.rp.dedup.core.model.ScannedContact
import com.rp.dedup.core.repository.ContactScannerRepository
import com.rp.dedup.core.notifications.ToastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ContactScannerViewModel(
    private val repository: ContactScannerRepository,
    private val toastManager: ToastManager
) : ViewModel() {

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedContact>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedContact>>> = _duplicateGroups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Non-null while the merge-selection dialog is open. */
    private val _mergePreview = MutableStateFlow<List<MergePreviewGroup>?>(null)
    val mergePreview: StateFlow<List<MergePreviewGroup>?> = _mergePreview.asStateFlow()

    private val _isPreparingMerge = MutableStateFlow(false)
    val isPreparingMerge: StateFlow<Boolean> = _isPreparingMerge.asStateFlow()

    // ── Scanning ──────────────────────────────────────────────────────────────

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
        val nameGroups = contacts.groupBy { it.name }.filter { it.value.size > 1 }
        val phoneMap = mutableMapOf<String, MutableList<ScannedContact>>()
        contacts.forEach { contact ->
            contact.phoneNumbers.forEach { num ->
                val normalized = num.replace("[^0-9]".toRegex(), "")
                if (normalized.length >= 10)
                    phoneMap.getOrPut(normalized) { mutableListOf() }.add(contact)
            }
        }
        val phoneGroups = phoneMap.filter { it.value.size > 1 }.values
        _duplicateGroups.value = (nameGroups.values + phoneGroups).distinctBy { group ->
            group.map { it.id }.sorted()
        }
    }

    // ── Merge flow ────────────────────────────────────────────────────────────

    /**
     * Queries all phone/email data rows for every group that has selected duplicates,
     * then exposes the result via [mergePreview] so the UI can show the selection dialog.
     */
    fun prepareMergePreview(selectedIds: List<String>) {
        val groups = _duplicateGroups.value
        _isPreparingMerge.value = true
        viewModelScope.launch {
            val previewGroups = mutableListOf<MergePreviewGroup>()

            for (group in groups) {
                val primary = group.firstOrNull() ?: continue
                val duplicatesInGroup = group.drop(1).filter { it.id in selectedIds }
                if (duplicatesInGroup.isEmpty()) continue

                // Collect phone + email entries for primary and each duplicate.
                val phoneEntries = mutableListOf<ContactDataEntry>()
                val emailEntries = mutableListOf<ContactDataEntry>()

                phoneEntries += repository.queryPhoneEntries(primary.id, primary.name, isPrimary = true)
                emailEntries += repository.queryEmailEntries(primary.id, primary.name, isPrimary = true)

                for (dup in duplicatesInGroup) {
                    phoneEntries += repository.queryPhoneEntries(dup.id, dup.name, isPrimary = false)
                    emailEntries += repository.queryEmailEntries(dup.id, dup.name, isPrimary = false)
                }

                previewGroups.add(MergePreviewGroup(
                    primaryId    = primary.id,
                    primaryName  = primary.name,
                    duplicateIds = duplicatesInGroup.map { it.id },
                    phoneEntries = phoneEntries,
                    emailEntries = emailEntries
                ))
            }

            _isPreparingMerge.value = false
            _mergePreview.value = previewGroups.ifEmpty { null }
        }
    }

    fun dismissMergePreview() {
        _mergePreview.value = null
    }

    /**
     * Executes the merge for all groups in [preview] using the user's checkbox selections.
     *
     * [keptDataIds]: set of Data._IDs the user checked to keep.
     * - Primary entries NOT in this set → deleted from the primary.
     * - Duplicate entries IN this set    → copied into the primary.
     */
    fun executeConfirmedMerge(
        preview: List<MergePreviewGroup>,
        keptDataIds: Set<String>,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            var anyFailure = false

            for (group in preview) {
                val allEntries = group.phoneEntries + group.emailEntries

                val primaryDataIdsToRemove = allEntries
                    .filter { it.isFromPrimary && it.dataId !in keptDataIds }
                    .map { it.dataId }
                    .toSet()

                val entriesToAdd = allEntries
                    .filter { !it.isFromPrimary && it.dataId in keptDataIds }

                repository.mergeContactsWithSelection(
                    keepId                    = group.primaryId,
                    duplicateIds              = group.duplicateIds,
                    primaryDataIdsToRemove    = primaryDataIdsToRemove,
                    entriesToAddFromDuplicates = entriesToAdd
                ).onFailure { anyFailure = true }
            }

            if (anyFailure)
                toastManager.showShort("Some contacts could not be merged")
            else
                toastManager.showShort("Contacts merged successfully")

            // Remove merged duplicate IDs from UI.
            val mergedDuplicateIds = preview.flatMap { it.duplicateIds }.toSet()
            val primaryIds = preview.map { it.primaryId }.toSet()
            _duplicateGroups.value = _duplicateGroups.value
                .map { group -> group.filterNot { it.id in mergedDuplicateIds && it.id !in primaryIds } }
                .filter { it.size > 1 }

            _mergePreview.value = null
            onComplete()
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ContactScannerViewModel(
                    ContactScannerRepository(context),
                    ToastManager(context)
                ) as T
        }
    }
}
