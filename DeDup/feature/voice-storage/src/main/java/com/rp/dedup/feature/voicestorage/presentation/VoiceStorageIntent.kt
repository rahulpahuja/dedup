package com.rp.dedup.feature.voicestorage.presentation

import android.net.Uri
import com.rp.dedup.feature.voicestorage.domain.FilterConfig

sealed interface VoiceStorageIntent {
    data object StartListening : VoiceStorageIntent
    data object StopListening : VoiceStorageIntent
    data class UpdateQuery(val query: String) : VoiceStorageIntent
    data class UpdateFilters(val filters: FilterConfig) : VoiceStorageIntent
    data class ToggleSelectFile(val uri: Uri) : VoiceStorageIntent
    data object RequestDeletion : VoiceStorageIntent
    data object DismissDeletion : VoiceStorageIntent
    data class OnDeletionResult(val granted: Boolean) : VoiceStorageIntent
    data object ClearSelection : VoiceStorageIntent
    data object ClearMicError : VoiceStorageIntent
}
