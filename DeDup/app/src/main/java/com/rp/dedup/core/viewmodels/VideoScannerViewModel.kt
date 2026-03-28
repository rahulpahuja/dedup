package com.rp.dedup.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.repository.VideoScannerRepository
import com.rp.dedup.core.data.ScanHistory
import com.rp.dedup.core.data.ScannedVideo
import com.rp.dedup.core.repository.ScanHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

private const val BATCH_SIZE = 50

class VideoScannerViewModel(
    private val repository: VideoScannerRepository,
    private val historyRepository: ScanHistoryRepository? = null
) : ViewModel() {

    private val _videos = MutableStateFlow<List<ScannedVideo>>(emptyList())
    val videos: StateFlow<List<ScannedVideo>> = _videos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScanning() {
        if (_isScanning.value) return
        val startTime = System.currentTimeMillis()
        var wasCancelled = false
        _isScanning.value = true
        _videos.value = emptyList()

        scanJob = viewModelScope.launch {
            try {
                val batch = mutableListOf<ScannedVideo>()

                repository.scanVideos()
                    .buffer(capacity = Channel.BUFFERED)
                    .collect { video ->
                        batch.add(video)
                        if (batch.size >= BATCH_SIZE) {
                            _videos.value = _videos.value + batch
                            batch.clear()
                        }
                    }

                if (batch.isNotEmpty()) {
                    _videos.value = _videos.value + batch
                }
            } catch (_: CancellationException) {
                wasCancelled = true
            } finally {
                _isScanning.value = false
                withContext(NonCancellable + Dispatchers.IO) {
                    historyRepository?.insert(
                        ScanHistory(
                            scanType = "VIDEO",
                            timestamp = startTime,
                            durationMs = System.currentTimeMillis() - startTime,
                            totalScanned = _videos.value.size,
                            duplicateGroups = 0,
                            totalDuplicates = 0,
                            reclaimableBytes = 0L,
                            status = if (wasCancelled) "CANCELLED" else "COMPLETED"
                        )
                    )
                }
            }
        }
    }

    fun cancelScanning() {
        scanJob?.cancel()
    }
}
