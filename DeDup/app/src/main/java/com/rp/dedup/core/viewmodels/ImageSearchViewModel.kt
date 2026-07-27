package com.rp.dedup.core.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.search.EmbedderProvider
import com.rp.dedup.core.search.ImageSearchRepository
import com.rp.dedup.core.search.SemanticSearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class ImageSearchViewModel(
    private val repository: SemanticSearchRepository
) : ViewModel() {

    private val _results = MutableStateFlow<List<ImageSearchRepository.SearchResult>>(emptyList())
    val results: StateFlow<List<ImageSearchRepository.SearchResult>> = _results

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _progress = MutableStateFlow(0 to 0)
    val progress: StateFlow<Pair<Int, Int>> = _progress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Debounce source: setting to "" from clear() prevents any pending debounced search from firing.
    private val queryState = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            queryState
                .debounce(400L)
                .collect { query ->
                    if (query.isNotBlank()) launchSearch(query)
                }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) { clear(); return }
        android.util.Log.d("ImageSearchVM", "Search enqueued: '$query'")
        queryState.value = query
    }

    private fun launchSearch(query: String) {
        searchJob?.cancel()
        // SemanticSearchRepository.search() uses withContext(Dispatchers.IO) internally,
        // so we don't specify a dispatcher here — this keeps the launch on viewModelScope's
        // dispatcher, which in tests is the test dispatcher (fully controlled by advanceUntilIdle).
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _results.value = emptyList()
            _error.value = null
            _progress.value = 0 to 0
            try {
                val found = repository.search(query) { labeled, total ->
                    _progress.value = labeled to total
                }
                _results.value = found
                android.util.Log.d("ImageSearchVM", "Results: ${found.size} items")
            } catch (_: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("ImageSearchVM", "Search cancelled")
            } catch (e: Exception) {
                android.util.Log.e("ImageSearchVM", "Search failed", e)
                _error.value = "Search failed: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun removeDeletedResult(uri: android.net.Uri) {
        _results.value = _results.value.filter { it.uri != uri }
    }

    fun clear() {
        queryState.value = ""
        searchJob?.cancel()
        _results.value = emptyList()
        _isSearching.value = false
        _progress.value = 0 to 0
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext  = context.applicationContext
            val dao         = AppDatabase.getDatabase(appContext).imageEmbeddingDao()
            val embedder    = EmbedderProvider(appContext)
            val likeRepo    = ImageSearchRepository(appContext)
            val semanticRepo = SemanticSearchRepository(dao, embedder, likeRepo)
            @Suppress("UNCHECKED_CAST")
            return ImageSearchViewModel(semanticRepo) as T
        }
    }
}
