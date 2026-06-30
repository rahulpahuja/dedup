package com.rp.dedup.core.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.model.ScanHistory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScanHistoryViewModel(private val repository: ScanHistoryRepository) : ViewModel() {

    companion object {
        class Factory(private val context: Context) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScanHistoryViewModel(
                    ScanHistoryRepository(AppDatabase.getDatabase(context).scanHistoryDao())
                ) as T
        }
    }

    val history: StateFlow<List<ScanHistory>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5_000), emptyList())

    fun delete(scan: ScanHistory) {
        viewModelScope.launch { repository.delete(scan) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }
}