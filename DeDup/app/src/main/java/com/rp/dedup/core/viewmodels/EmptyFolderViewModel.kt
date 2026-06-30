package com.rp.dedup.core.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.model.EmptyFolder
import com.rp.dedup.core.model.state.EmptyFolderState
import com.rp.dedup.core.deepoptimization.EmptyFolderRepository
import com.rp.dedup.core.deepoptimization.EmptyFolderRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmptyFolderViewModel(
    private val repository: EmptyFolderRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val analyticsManager: AnalyticsManager? = null
) : ViewModel() {

    private val _state = MutableStateFlow<EmptyFolderState>(EmptyFolderState.Idle)
    val state: StateFlow<EmptyFolderState> = _state.asStateFlow()

    // treeUri: SAF tree URI on Android 11+ (passed by the screen after the user grants access);
    //          null on Android < 11 (File API is used instead).
    fun startScan(treeUri: Uri? = null) {
        if (_state.value is EmptyFolderState.Scanning) return
        viewModelScope.launch(ioDispatcher) {
            _state.value = EmptyFolderState.Scanning
            analyticsManager?.logScanStarted("EMPTY_FOLDER")
            try {
                val folders = repository.findEmptyFolders(treeUri)
                analyticsManager?.logScanCompleted(
                    scanType       = "EMPTY_FOLDER",
                    totalScanned   = folders.size,
                    duplicatesFound = folders.size,
                    reclaimableBytes = 0L
                )
                _state.value = EmptyFolderState.Results(folders)
            } catch (e: Exception) {
                analyticsManager?.logError("EMPTY_FOLDER", e.localizedMessage ?: "Scan failed")
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
            analyticsManager?.logFilesDeleted("EMPTY_FOLDER", deletedPaths.size, 0L)
            _state.value = EmptyFolderState.Results(
                current.folders.filterNot { it.path in deletedPaths }
            )
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return EmptyFolderViewModel(
                    repository       = EmptyFolderRepositoryImpl(context),
                    analyticsManager = AnalyticsManager.getInstance(context)
                ) as T
            }
        }
    }
}
