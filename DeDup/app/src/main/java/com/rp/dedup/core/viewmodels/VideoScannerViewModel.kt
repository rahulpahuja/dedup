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

class VideoScannerViewModel(
    private val repository: VideoScannerRepository,
    private val historyRepository: ScanHistoryRepository? = null
) : ViewModel() {

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedVideo>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedVideo>>> = _duplicateGroups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedCount = MutableStateFlow(0)
    val scannedCount: StateFlow<Int> = _scannedCount.asStateFlow()

    private var scanJob: Job? = null

    fun startScanning() {
        if (_isScanning.value) return
        val startTime = System.currentTimeMillis()
        var wasCancelled = false
        _isScanning.value = true
        _scannedCount.value = 0
        _duplicateGroups.value = emptyList()

        scanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val allVideos = mutableListOf<ScannedVideo>()

                repository.scanVideos()
                    .buffer(capacity = Channel.BUFFERED)
                    .collect { video ->
                        allVideos.add(video)
                        _scannedCount.value = allVideos.size
                    }

                val duplicates = findDuplicates(allVideos)
                _duplicateGroups.value = duplicates

            } catch (_: CancellationException) {
                wasCancelled = true
            } finally {
                _isScanning.value = false
                val totalCount = _scannedCount.value
                withContext(NonCancellable + Dispatchers.IO) {
                    val groups = _duplicateGroups.value
                    historyRepository?.insert(
                        ScanHistory(
                            scanType = "VIDEO",
                            timestamp = startTime,
                            durationMs = System.currentTimeMillis() - startTime,
                            totalScanned = totalCount,
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
        // More robust grouping:
        // 1. Group by size first.
        // 2. Only consider groups where size > 0 and count > 1.
        // 3. Within those groups, if duration is available (>0), verify it's similar (within 1 second).
        
        return allVideos
            .filter { it.sizeInBytes > 0 }
            .groupBy { it.sizeInBytes }
            .filter { it.value.size > 1 }
            .flatMap { entry ->
                val sizeGroup = entry.value
                // If all have valid duration, sub-group by duration (within 1s tolerance)
                // Otherwise, treat the size match as enough for a "potential duplicate" 
                // (True duplicates will have identical size down to the byte)
                if (sizeGroup.all { it.durationMs > 0 }) {
                    sizeGroup.groupBy { it.durationMs / 1000 } // Group by seconds
                        .filter { it.value.size > 1 }
                        .values
                } else {
                    listOf(sizeGroup)
                }
            }
            .toList()
    }

    fun cancelScanning() {
        scanJob?.cancel()
    }
}
