package com.rp.dedup.core.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.data.WhatsAppScanResult
import com.rp.dedup.core.deepoptimization.WhatsAppCleanerRepository
import com.rp.dedup.core.deepoptimization.WhatsAppCleanerRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class WhatsAppCleanerState {
    object Idle : WhatsAppCleanerState()
    data class Scanning(val phase: String) : WhatsAppCleanerState()
    data class Results(val data: WhatsAppScanResult) : WhatsAppCleanerState()
    data class Error(val message: String) : WhatsAppCleanerState()
}

class WhatsAppCleanerViewModel(
    private val repository: WhatsAppCleanerRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow<WhatsAppCleanerState>(WhatsAppCleanerState.Idle)
    val state: StateFlow<WhatsAppCleanerState> = _state.asStateFlow()

    fun startScan() {
        if (_state.value is WhatsAppCleanerState.Scanning) return
        viewModelScope.launch(ioDispatcher) {
            _state.value = WhatsAppCleanerState.Scanning("Scanning WhatsApp folders…")
            try {
                val result = repository.scanAll()
                _state.value = WhatsAppCleanerState.Results(result)
            } catch (e: Exception) {
                _state.value = WhatsAppCleanerState.Error(e.localizedMessage ?: "Scan failed")
            }
        }
    }

    fun deleteFiles(uris: List<Uri>) {
        val current = _state.value as? WhatsAppCleanerState.Results ?: return
        viewModelScope.launch(ioDispatcher) {
            repository.deleteFiles(uris)
            val removed = uris.toSet()
            val updated = current.data.copy(
                duplicateMedia = current.data.duplicateMedia
                    .map { g -> g.filterNot { it.uri in removed } }.filter { it.size >= 2 },
                duplicateStatuses = current.data.duplicateStatuses
                    .map { g -> g.filterNot { it.uri in removed } }.filter { it.size >= 2 },
                duplicateDocs = current.data.duplicateDocs
                    .map { g -> g.filterNot { it.uri in removed } }.filter { it.size >= 2 },
                largeFiles = current.data.largeFiles.filterNot { it.uri in removed },
                sentReceivedMatches = current.data.sentReceivedMatches
                    .filterNot { it.sent.uri in removed || it.received.uri in removed }
            )
            _state.value = WhatsAppCleanerState.Results(updated)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WhatsAppCleanerViewModel(WhatsAppCleanerRepositoryImpl(context)) as T
            }
        }
    }
}
