package com.rp.dedup.feature.voicestorage.presentation

import android.net.Uri
import com.rp.dedup.feature.voicestorage.data.model.StorageItem
import com.rp.dedup.feature.voicestorage.domain.FilterConfig

data class VoiceStorageState(
    val currentQueryText: String = "",
    val isListening: Boolean = false,
    val isLoading: Boolean = false,
    val filteredFiles: List<StorageItem> = emptyList(),
    val selectedFileUris: Set<Uri> = emptySet(),
    val activeFilters: FilterConfig = FilterConfig(),
    val showDeleteConfirmation: Boolean = false,
    val voiceCommandSummary: String = "",
    // Non-null while a mic error toast should be shown; cleared after display
    val micError: String? = null,
)
