package com.rp.dedup.core.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.data.EmptyFolder
import com.rp.dedup.core.deepoptimization.EmptyFolderRepository
import com.rp.dedup.core.deepoptimization.EmptyFolderRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class EmptyFolderState {
    object Idle : EmptyFolderState()
    object Scanning : EmptyFolderState()
    data class Results(val folders: List<EmptyFolder>) : EmptyFolderState()
    data class Error(val message: String) : EmptyFolderState()
}

class EmptyFolderViewModel(
    private val repository: EmptyFolderRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow<EmptyFolderState>(EmptyFolderState.Idle)
    val state: StateFlow<EmptyFolderState> = _state.asStateFlow()

    fun startScan() {
        if (_state.value is EmptyFolderState.Scanning) return
        viewModelScope.launch(ioDispatcher) {
            _state.value = EmptyFolderState.Scanning
            try {
                val folders = repository.findEmptyFolders()
                _state.value = EmptyFolderState.Results(folders)
            } catch (e: Exception) {
                _state.value = EmptyFolderState.Error(e.localizedMessage ?: "Scan failed")
            }
        }
    }

    fun deleteFolders(folders: List<EmptyFolder>) {
        viewModelScope.launch(ioDispatcher) {
            val current = _state.value as? EmptyFolderState.Results ?: return@launch
            val deletedPaths = folders
                .filter { repository.deleteFolder(it) }
                .map { it.path }
                .toSet()
            _state.value = EmptyFolderState.Results(
                current.folders.filterNot { it.path in deletedPaths }
            )
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return EmptyFolderViewModel(EmptyFolderRepositoryImpl(context)) as T
            }
        }
    }
}
