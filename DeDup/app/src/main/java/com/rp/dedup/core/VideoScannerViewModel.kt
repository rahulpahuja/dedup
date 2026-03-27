package com.rp.dedup.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch

private const val BATCH_SIZE = 50

class VideoScannerViewModel(private val repository: VideoScannerRepository) : ViewModel() {

    private val _videos = MutableStateFlow<List<ScannedVideo>>(emptyList())
    val videos: StateFlow<List<ScannedVideo>> = _videos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        _videos.value = emptyList()

        scanJob = viewModelScope.launch {
            val batch = mutableListOf<ScannedVideo>()

            // buffer() decouples the IO-bound producer from this collector so the
            // MediaStore cursor is never blocked waiting for the UI to update.
            repository.scanVideos()
                .buffer(capacity = Channel.BUFFERED)
                .collect { video ->
                    batch.add(video)
                    if (batch.size >= BATCH_SIZE) {
                        _videos.value = _videos.value + batch
                        batch.clear()
                    }
                }

            // flush any remainder
            if (batch.isNotEmpty()) {
                _videos.value = _videos.value + batch
            }

            _isScanning.value = false
        }
    }

    fun cancelScanning() {
        scanJob?.cancel()
        _isScanning.value = false
    }
}
