package com.rp.dedup.core.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.compression.CompressionResult
import com.rp.dedup.core.compression.ImageCompressionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CompressionCandidate(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val isSelected: Boolean = true
)

data class CompressionUiState(
    val candidates: List<CompressionCandidate> = emptyList(),
    val isLoading: Boolean = false,
    val isCompressing: Boolean = false,
    val progress: Pair<Int, Int> = 0 to 0,
    val results: List<CompressionResult> = emptyList(),
    val totalSavedBytes: Long = 0L,
    val quality: Int = 85,
    val deleteOriginals: Boolean = true,
    val error: String? = null,
    val minSizeKb: Int = 500
)

class ImageCompressionViewModel(
    private val repository: ImageCompressionRepository
) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ImageCompressionViewModel(
                ImageCompressionRepository(context.applicationContext)
            ) as T
        }
    }

    private val _state = MutableStateFlow(CompressionUiState())
    val state: StateFlow<CompressionUiState> = _state.asStateFlow()

    init { loadCandidates() }

    fun setQuality(quality: Int) { _state.value = _state.value.copy(quality = quality) }

    fun setDeleteOriginals(delete: Boolean) { _state.value = _state.value.copy(deleteOriginals = delete) }

    fun setMinSizeKb(kb: Int) {
        _state.value = _state.value.copy(minSizeKb = kb)
        loadCandidates()
    }

    fun toggleSelection(uri: Uri) {
        _state.value = _state.value.copy(
            candidates = _state.value.candidates.map {
                if (it.uri == uri) it.copy(isSelected = !it.isSelected) else it
            }
        )
    }

    fun selectAll(selected: Boolean) {
        _state.value = _state.value.copy(
            candidates = _state.value.candidates.map { it.copy(isSelected = selected) }
        )
    }

    fun loadCandidates() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val minBytes = _state.value.minSizeKb * 1024L
                val images = repository.loadCompressibleImages(minBytes)
                _state.value = _state.value.copy(
                    candidates = images.map { (uri, name, size) ->
                        CompressionCandidate(uri, name, size)
                    },
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun compressSelected() {
        val selected = _state.value.candidates.filter { it.isSelected }
        if (selected.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(
                isCompressing = true, results = emptyList(), totalSavedBytes = 0L
            )
            val accumulatedResults = mutableListOf<CompressionResult>()
            selected.forEachIndexed { idx, candidate ->
                _state.value = _state.value.copy(progress = idx to selected.size)
                val result = repository.compress(
                    uri            = candidate.uri,
                    quality        = _state.value.quality,
                    deleteOriginal = _state.value.deleteOriginals
                )
                if (result != null) accumulatedResults.add(result)
                _state.value = _state.value.copy(
                    results        = accumulatedResults.toList(),
                    totalSavedBytes = accumulatedResults.sumOf { it.savedBytes }
                )
            }
            _state.value = _state.value.copy(
                isCompressing = false,
                progress      = selected.size to selected.size
            )
            // Reload remaining candidates
            loadCandidates()
        }
    }
}
