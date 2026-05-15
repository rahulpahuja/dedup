package com.rp.dedup.core.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.search.SmartJunkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SmartJunkState {
    object Idle : SmartJunkState()
    data class Scanning(val progress: Float) : SmartJunkState()
    data class Results(val groups: Map<SmartJunkRepository.JunkCategory, List<SmartJunkRepository.JunkItem>>) : SmartJunkState()
    data class Error(val message: String) : SmartJunkState()
}

class SmartJunkViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmartJunkRepository(application)

    private val _uiState = MutableStateFlow<SmartJunkState>(SmartJunkState.Idle)
    val uiState: StateFlow<SmartJunkState> = _uiState.asStateFlow()

    fun startScan() {
        viewModelScope.launch {
            _uiState.value = SmartJunkState.Scanning(0f)
            try {
                val results = repository.scanForJunk { scanned, total ->
                    _uiState.value = SmartJunkState.Scanning(scanned.toFloat() / total)
                }
                _uiState.value = SmartJunkState.Results(results)
            } catch (e: Exception) {
                _uiState.value = SmartJunkState.Error(e.localizedMessage ?: "Unknown error during scan")
            }
        }
    }

    fun clear() {
        _uiState.value = SmartJunkState.Idle
    }
}
