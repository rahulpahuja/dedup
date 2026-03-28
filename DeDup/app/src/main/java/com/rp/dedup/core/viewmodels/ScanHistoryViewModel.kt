package com.rp.dedup.core.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.data.ScanHistory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScanHistoryViewModel(private val repository: ScanHistoryRepository) : ViewModel() {

    val history: StateFlow<List<ScanHistory>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5_000), emptyList())

    fun delete(scan: ScanHistory) {
        viewModelScope.launch { repository.delete(scan) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}