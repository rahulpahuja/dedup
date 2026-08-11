package com.rp.dedup.core.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.dao.ImageEmbeddingDao
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.MemoriesRepository
import com.rp.dedup.core.repository.MemoryGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MemoriesViewModel(
    private val dao: ImageEmbeddingDao,
    private val repository: MemoriesRepository,
) : ViewModel() {

    private val _memories = MutableStateFlow<List<MemoryGroup>>(emptyList())
    val memories: StateFlow<List<MemoryGroup>> = _memories

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val uris = dao.getAllUris().map { Uri.parse(it) }
            _memories.value = repository.findMemories(uris)
            _isLoading.value = false
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val dao = AppDatabase.getDatabase(appContext).imageEmbeddingDao()
            @Suppress("UNCHECKED_CAST")
            return MemoriesViewModel(dao, MemoriesRepository(appContext)) as T
        }
    }
}
