package com.rp.dedup.core.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.SemanticDuplicateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SemanticScanState(
    val groups: List<List<Uri>> = emptyList(),
    val isScanning: Boolean = false,
    val progress: Pair<Int, Int> = 0 to 0,
    val error: String? = null,
    val indexedCount: Int = 0
)

class SemanticScannerViewModel(
    private val repository: SemanticDuplicateRepository,
    private val embeddingDao: com.rp.dedup.core.dao.ImageEmbeddingDao
) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val dao = AppDatabase.getDatabase(appContext).imageEmbeddingDao()
            return SemanticScannerViewModel(
                SemanticDuplicateRepository(dao),
                dao
            ) as T
        }
    }

    private val _state = MutableStateFlow(SemanticScanState())
    val state: StateFlow<SemanticScanState> = _state.asStateFlow()

    init { checkIndex() }

    private fun checkIndex() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = embeddingDao.count()
            _state.value = _state.value.copy(indexedCount = count)
        }
    }

    fun scan() {
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = _state.value.copy(isScanning = true, error = null, groups = emptyList())
            try {
                val groups = repository.findDuplicateGroups { done, total ->
                    _state.value = _state.value.copy(progress = done to total)
                }
                _state.value = _state.value.copy(groups = groups, isScanning = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isScanning = false,
                    error = e.message ?: "Scan failed"
                )
            }
        }
    }

    fun dismissGroup(group: List<Uri>) {
        _state.value = _state.value.copy(
            groups = _state.value.groups.filter { it != group }
        )
    }
}
