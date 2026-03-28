package com.rp.dedup.core.viewmodels

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

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedVideo>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedVideo>>> = _duplicateGroups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScanning() {
        if (_isScanning.value) return
        val startTime = System.currentTimeMillis()
        var wasCancelled = false
        _isScanning.value = true
        _duplicateGroups.value = emptyList()

        scanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val allVideos = mutableListOf<ScannedVideo>()

                repository.scanVideos()
                    .buffer(capacity = Channel.BUFFERED)
                    .collect { video ->
                        allVideos.add(video)
                    }

                val duplicates = findDuplicates(allVideos)
                _duplicateGroups.value = duplicates

            } catch (_: CancellationException) {
                wasCancelled = true
            } finally {
                _isScanning.value = false
                withContext(NonCancellable + Dispatchers.IO) {
                    val groups = _duplicateGroups.value
                    historyRepository?.insert(
                        ScanHistory(
                            scanType = "VIDEO",
                            timestamp = startTime,
                            durationMs = System.currentTimeMillis() - startTime,
                            totalScanned = 0, // We could track total scanned if needed
                            duplicateGroups = groups.size,
                            totalDuplicates = groups.sumOf { it.size - 1 },
                            reclaimableBytes = groups.sumOf { group ->
                                group.drop(1).sumOf { it.sizeInBytes }
                            },
                            status = if (wasCancelled) "CANCELLED" else "COMPLETED"
                        )
                    )
                }
            }
        }
    }

    private fun findDuplicates(allVideos: List<ScannedVideo>): List<List<ScannedVideo>> {
        // Group by size and duration as a strong heuristic for duplicate videos
        return allVideos.groupBy { "${it.sizeInBytes}_${it.durationMs}" }
            .filter { it.value.size > 1 }
            .values
            .toList()
    }

    fun cancelScanning() {
        scanJob?.cancel()
    }
}
