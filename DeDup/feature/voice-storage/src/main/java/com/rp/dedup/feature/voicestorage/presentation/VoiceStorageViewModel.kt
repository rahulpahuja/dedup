package com.rp.dedup.feature.voicestorage.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.speech.SpeechRecognizer
import com.rp.dedup.feature.voicestorage.data.model.SpeechResult
import com.rp.dedup.feature.voicestorage.data.repository.LocalStorageRepository
import com.rp.dedup.feature.voicestorage.data.source.VoiceCaptureDataSource
import com.rp.dedup.feature.voicestorage.domain.FilterConfig
import com.rp.dedup.feature.voicestorage.domain.VoiceQueryParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceStorageViewModel(
    private val voiceCaptureDataSource: VoiceCaptureDataSource,
    private val localStorageRepository: LocalStorageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceStorageState())
    val state: StateFlow<VoiceStorageState> = _state.asStateFlow()

    private var listeningJob: Job? = null
    private var fetchJob: Job? = null

    init {
        fetchFiles()
    }

    fun onIntent(intent: VoiceStorageIntent) {
        when (intent) {
            VoiceStorageIntent.StartListening    -> startListening()
            VoiceStorageIntent.StopListening     -> stopListening()
            is VoiceStorageIntent.UpdateQuery    -> updateQuery(intent.query)
            is VoiceStorageIntent.UpdateFilters  -> updateFilters(intent.filters)
            is VoiceStorageIntent.ToggleSelectFile -> toggleSelection(intent.uri)
            VoiceStorageIntent.RequestDeletion   -> _state.update { it.copy(showDeleteConfirmation = true) }
            VoiceStorageIntent.DismissDeletion   -> _state.update { it.copy(showDeleteConfirmation = false) }
            is VoiceStorageIntent.OnDeletionResult -> onDeletionResult(intent.granted)
            VoiceStorageIntent.ClearSelection    -> _state.update { it.copy(selectedFileUris = emptySet()) }
            VoiceStorageIntent.ClearMicError     -> _state.update { it.copy(micError = null) }
        }
    }

    private fun startListening() {
        listeningJob?.cancel()
        _state.update { it.copy(isListening = true, micError = null) }
        listeningJob = viewModelScope.launch {
            voiceCaptureDataSource.transcriptionFlow()
                .catch { e ->
                    _state.update { s ->
                        s.copy(isListening = false, micError = e.message ?: "Voice recognition failed")
                    }
                }
                .collect { result ->
                    when (result) {
                        is SpeechResult.Partial -> {
                            _state.update { it.copy(currentQueryText = result.text) }
                        }
                        is SpeechResult.Final -> {
                            val parsed = VoiceQueryParser.parse(result.text)
                            _state.update {
                                it.copy(
                                    currentQueryText     = parsed.nameQuery,
                                    activeFilters        = parsed.filters,
                                    voiceCommandSummary  = parsed.summary,
                                    isListening          = false,
                                )
                            }
                            fetchFiles()
                        }
                        is SpeechResult.Error -> {
                            _state.update { it.copy(isListening = false, micError = speechErrorMessage(result.code)) }
                        }
                    }
                }
        }
    }

    private fun speechErrorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NO_MATCH                 -> "No speech detected — try again"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT           -> "No speech detected — try speaking sooner"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT          -> "Network error — check connection and try again"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY          -> "Recognizer busy — try again in a moment"
        SpeechRecognizer.ERROR_CLIENT                   -> "Recognition client error — please try again"
        else                                            -> "Voice recognition failed (code $code)"
    }

    private fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        _state.update { it.copy(isListening = false) }
        fetchFiles()
    }

    private fun updateQuery(query: String) {
        _state.update { it.copy(currentQueryText = query, voiceCommandSummary = "") }
        fetchFiles()
    }

    private fun updateFilters(filters: FilterConfig) {
        _state.update { it.copy(activeFilters = filters) }
        fetchFiles()
    }

    private fun toggleSelection(uri: Uri) {
        _state.update { state ->
            val next = state.selectedFileUris.toMutableSet().apply {
                if (uri in this) remove(uri) else add(uri)
            }
            state.copy(selectedFileUris = next)
        }
    }

    private fun onDeletionResult(granted: Boolean) {
        _state.update { it.copy(showDeleteConfirmation = false, selectedFileUris = emptySet()) }
        if (granted) fetchFiles()
    }

    private fun fetchFiles() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val snap = _state.value
            localStorageRepository.queryFiles(snap.currentQueryText, snap.activeFilters)
                .catch { _state.update { s -> s.copy(isLoading = false) } }
                .collect { files -> _state.update { it.copy(filteredFiles = files, isLoading = false) } }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = VoiceStorageViewModel(
            voiceCaptureDataSource  = VoiceCaptureDataSource(context.applicationContext),
            localStorageRepository  = LocalStorageRepository(context.applicationContext),
        ) as T
    }
}
