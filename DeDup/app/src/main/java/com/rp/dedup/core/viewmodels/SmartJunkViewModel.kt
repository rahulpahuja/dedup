package com.rp.dedup.core.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.model.SmartJunkState
import com.rp.dedup.core.search.SmartJunkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SmartJunkViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmartJunkRepository(application)
    private val analyticsManager = AnalyticsManager(application)

    private val _uiState = MutableStateFlow<SmartJunkState>(SmartJunkState.Idle)
    val uiState: StateFlow<SmartJunkState> = _uiState.asStateFlow()

    fun startScan() {
        viewModelScope.launch {
            _uiState.value = SmartJunkState.Scanning(0f)
            analyticsManager.logScanStarted("JUNK")
            try {
                val results = repository.scanForJunk { scanned, total ->
                    _uiState.value = SmartJunkState.Scanning(scanned.toFloat() / total)
                }
                _uiState.value = SmartJunkState.Results(results)
                
                val totalFound = results.values.sumOf { it.size }
                analyticsManager.logScanCompleted("JUNK", totalFound, totalFound, 0L) // reclaimable bytes not easily available here
            } catch (e: Exception) {
                _uiState.value = SmartJunkState.Error(e.localizedMessage ?: "Unknown error during scan")
            }
        }
    }

    fun toggleCategoryExpansion(category: SmartJunkRepository.JunkCategory) {
        val currentState = _uiState.value
        if (currentState is SmartJunkState.Results) {
            val currentExpanded = currentState.expandedCategories
            val newExpanded = if (currentExpanded.contains(category)) {
                currentExpanded - category
            } else {
                currentExpanded + category
            }
            _uiState.value = currentState.copy(expandedCategories = newExpanded)
        }
    }

    fun toggleSelection(uri: Uri) {
        val currentState = _uiState.value
        if (currentState is SmartJunkState.Results) {
            val currentSelected = currentState.selectedUris
            val newSelected = if (currentSelected.contains(uri)) {
                currentSelected - uri
            } else {
                currentSelected + uri
            }
            _uiState.value = currentState.copy(selectedUris = newSelected)
        }
    }

    fun selectAllInCategory(category: SmartJunkRepository.JunkCategory) {
        val currentState = _uiState.value
        if (currentState is SmartJunkState.Results) {
            val items = currentState.groups[category] ?: return
            val categoryUris = items.map { it.uri }.toSet()
            _uiState.value = currentState.copy(selectedUris = currentState.selectedUris + categoryUris)
        }
    }

    fun deselectAllInCategory(category: SmartJunkRepository.JunkCategory) {
        val currentState = _uiState.value
        if (currentState is SmartJunkState.Results) {
            val items = currentState.groups[category] ?: return
            val categoryUris = items.map { it.uri }.toSet()
            _uiState.value = currentState.copy(selectedUris = currentState.selectedUris - categoryUris)
        }
    }

    fun removeDeletedItems(deletedUris: List<Uri>) {
        val currentState = _uiState.value
        if (currentState is SmartJunkState.Results) {
            val newGroups = currentState.groups.mapValues { (_, items) ->
                items.filterNot { it.uri in deletedUris }
            }.filterValues { it.isNotEmpty() }
            
            _uiState.value = currentState.copy(
                groups = newGroups,
                selectedUris = currentState.selectedUris - deletedUris.toSet()
            )

            analyticsManager.logFilesDeleted("JUNK", deletedUris.size, 0L)
        }
    }

    fun clear() {
        _uiState.value = SmartJunkState.Idle
    }
}
