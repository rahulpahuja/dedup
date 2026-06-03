package com.rp.dedup.core.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.model.WhatsAppCleanerState
import com.rp.dedup.core.model.WhatsAppScanResult
import com.rp.dedup.core.deepoptimization.WhatsAppCleanerRepository
import com.rp.dedup.core.deepoptimization.WhatsAppCleanerRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WhatsAppCleanerViewModel(
    private val repository: WhatsAppCleanerRepository,
    private val analyticsManager: AnalyticsManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow<WhatsAppCleanerState>(WhatsAppCleanerState.Idle)
    val state: StateFlow<WhatsAppCleanerState> = _state.asStateFlow()

    fun startScan() {
        if (_state.value is WhatsAppCleanerState.Scanning) return
        analyticsManager?.logScanStarted("WHATSAPP")
        viewModelScope.launch(ioDispatcher) {
            _state.value = WhatsAppCleanerState.Scanning("Scanning WhatsApp folders…")
            try {
                val result = repository.scanAll()
                
                val totalDuplicates = result.duplicateMedia.sumOf { it.size - 1 } + 
                                     result.duplicateStatuses.sumOf { it.size - 1 } + 
                                     result.duplicateDocs.sumOf { it.size - 1 }
                
                val totalReclaimable = result.duplicateMedia.sumOf { g -> g.drop(1).sumOf { it.size } } +
                                      result.duplicateStatuses.sumOf { g -> g.drop(1).sumOf { it.size } } +
                                      result.duplicateDocs.sumOf { g -> g.drop(1).sumOf { it.size } }

                analyticsManager?.logScanCompleted(
                    "WHATSAPP",
                    totalScanned = 0, // Not explicitly tracked in result
                    duplicatesFound = totalDuplicates,
                    reclaimableBytes = totalReclaimable
                )
                
                _state.value = WhatsAppCleanerState.Results(result)
            } catch (e: Exception) {
                analyticsManager?.logError("WHATSAPP", e.message ?: "Unknown Error")
                _state.value = WhatsAppCleanerState.Error(e.localizedMessage ?: "Scan failed")
            }
        }
    }

    fun deleteFiles(uris: List<Uri>) {
        val current = _state.value as? WhatsAppCleanerState.Results ?: return
        viewModelScope.launch(ioDispatcher) {
            // Calculate freed bytes before deleting
            val removed = uris.toSet()
            val allFiles = current.data.duplicateMedia.flatten() + 
                          current.data.duplicateStatuses.flatten() + 
                          current.data.duplicateDocs.flatten() + 
                          current.data.largeFiles +
                          current.data.sentReceivedMatches.flatMap { listOf(it.sent, it.received) }
            
            val freedBytes = allFiles.filter { it.uri in removed }.distinctBy { it.uri }.sumOf { it.size }
            
            repository.deleteFiles(uris)
            analyticsManager?.logFilesDeleted("WHATSAPP", uris.size, freedBytes)

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
                return WhatsAppCleanerViewModel(
                    WhatsAppCleanerRepositoryImpl(context),
                    AnalyticsManager(context)
                ) as T
            }
        }
    }
}
