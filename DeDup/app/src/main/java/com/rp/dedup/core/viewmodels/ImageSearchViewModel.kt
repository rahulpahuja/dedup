package com.rp.dedup.core.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.search.ImageSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ImageSearchViewModel(
    private val repository: ImageSearchRepository
) : ViewModel() {

    private val _results = MutableStateFlow<List<ImageSearchRepository.SearchResult>>(emptyList())
    val results: StateFlow<List<ImageSearchRepository.SearchResult>> = _results

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    /** Pair of (images labeled so far, total images to label). */
    private val _progress = MutableStateFlow(0 to 0)
    val progress: StateFlow<Pair<Int, Int>> = _progress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var searchJob: Job? = null

    fun search(query: String) {
        if (query.isBlank()) { clear(); return }

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            _results.value = emptyList()
            _error.value = null
            _progress.value = 0 to 0
            try {
                val found = repository.search(query) { labeled, total ->
                    _progress.value = labeled to total
                }
                _results.value = found
            } catch (_: kotlinx.coroutines.CancellationException) {
                // expected on clear()
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clear() {
        searchJob?.cancel()
        _results.value = emptyList()
        _isSearching.value = false
        _progress.value = 0 to 0
        _error.value = null
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ImageSearchViewModel(ImageSearchRepository(context.applicationContext)) as T
        }
    }
}
