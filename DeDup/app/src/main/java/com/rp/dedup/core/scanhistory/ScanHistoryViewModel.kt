package com.rp.dedup.core.scanhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScanHistoryViewModel(private val repository: ScanHistoryRepository) : ViewModel() {

    val history: StateFlow<List<ScanHistory>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(scan: ScanHistory) {
        viewModelScope.launch { repository.delete(scan) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}
