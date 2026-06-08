package com.rp.dedup.core.model.state

import android.net.Uri
import com.rp.dedup.core.search.SmartJunkRepository

sealed class SmartJunkState {
    object Idle : SmartJunkState()
    data class Scanning(val progress: Float) : SmartJunkState()
    data class Results(
        val groups: Map<SmartJunkRepository.JunkCategory, List<SmartJunkRepository.JunkItem>>,
        val selectedUris: Set<Uri> = emptySet(),
        val isGridView: Boolean = false,
        val expandedCategories: Set<SmartJunkRepository.JunkCategory> = emptySet()
    ) : SmartJunkState()
    data class Error(val message: String) : SmartJunkState()
}
