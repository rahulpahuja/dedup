package com.rp.dedup.core.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.model.TrashItem
import com.rp.dedup.core.repository.TrashRepository
import com.rp.dedup.core.trash.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class TrashUiEvent {
    data class RestoreSuccess(val name: String) : TrashUiEvent()
    data class RestoreFailed(val name: String)  : TrashUiEvent()
    object EmptiedTrash                         : TrashUiEvent()
}

class TrashViewModel(
    private val repository: TrashRepository,
    private val trashManager: TrashManager
) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val db         = AppDatabase.getDatabase(appContext)
            val repository = TrashRepository(db.trashDao())
            val manager    = TrashManager(appContext)
            return TrashViewModel(repository, manager) as T
        }
    }

    val items: StateFlow<List<TrashItem>> = repository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalSize: StateFlow<Long> = repository.getTotalSize()
        .map { it ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _event = MutableStateFlow<TrashUiEvent?>(null)
    val event: StateFlow<TrashUiEvent?> = _event.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        // Purge expired items on every ViewModel init (app open)
        viewModelScope.launch(Dispatchers.IO) {
            val expired = repository.deleteExpired(System.currentTimeMillis())
            trashManager.pruneExpiredFiles(expired)
        }
    }

    /**
     * Caches [uri] bytes to private storage and inserts a TrashItem record.
     * The caller must delete the original file from MediaStore separately.
     */
    fun moveToTrash(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = trashManager.prepareTrashItem(uri) ?: return@launch
            repository.insert(item)
        }
    }

    fun insert(item: TrashItem) {
        viewModelScope.launch(Dispatchers.IO) { repository.insert(item) }
    }

    fun restore(item: TrashItem) {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            val ok = trashManager.restore(item)
            if (ok) {
                repository.delete(item)
                _event.value = TrashUiEvent.RestoreSuccess(item.name)
            } else {
                _event.value = TrashUiEvent.RestoreFailed(item.name)
            }
            _isBusy.value = false
        }
    }

    fun deleteForever(item: TrashItem) {
        viewModelScope.launch(Dispatchers.IO) {
            trashManager.deleteForever(item)
            repository.delete(item)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            _isBusy.value = true
            val all = repository.clearAll()
            trashManager.pruneExpiredFiles(all)
            _event.value = TrashUiEvent.EmptiedTrash
            _isBusy.value = false
        }
    }

    fun clearEvent() { _event.value = null }
}
