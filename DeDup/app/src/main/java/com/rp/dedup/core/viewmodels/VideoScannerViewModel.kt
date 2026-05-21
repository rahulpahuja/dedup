package com.rp.dedup.core.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.repository.VideoScannerRepository
import com.rp.dedup.core.data.ScanHistory
import com.rp.dedup.core.data.ScannedVideo
import com.rp.dedup.core.repository.ScanHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
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
    private val historyRepository: ScanHistoryRepository? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedVideo>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedVideo>>> = _duplicateGroups.asStateFlow()

    private val _videos = MutableStateFlow<List<ScannedVideo>>(emptyList())
    val videos: StateFlow<List<ScannedVideo>> = _videos.asStateFlow()

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
        _videos.value = emptyList()
        _duplicateGroups.value = emptyList()

        scanJob = viewModelScope.launch(defaultDispatcher) {
            val allVideos = mutableListOf<ScannedVideo>()
            try {
                repository.scanVideos()
                    .buffer(capacity = Channel.BUFFERED)
                    .collect { video ->
                        allVideos.add(video)
                        _scannedCount.value = allVideos.size
                        if (allVideos.size % 50 == 0) {
                            _videos.value = allVideos.toList()
                        }
                    }

                _videos.value = allVideos.toList()
                val duplicates = findDuplicates(allVideos)
                _duplicateGroups.value = duplicates

            } catch (_: CancellationException) {
                wasCancelled = true
                _videos.value = allVideos.toList()
                _duplicateGroups.value = findDuplicates(allVideos)
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
        return allVideos
            .filter { it.sizeInBytes > 0 }
            .groupBy { it.sizeInBytes }
            .filter { it.value.size > 1 }
            .flatMap { entry ->
                val sizeGroup = entry.value
                val (validDurationVideos, invalidDurationVideos) = sizeGroup.partition { it.durationMs > 0 }
                
                val resultGroups = mutableListOf<List<ScannedVideo>>()
                
                if (validDurationVideos.isNotEmpty()) {
                    val sorted = validDurationVideos.sortedBy { it.durationMs }
                    val subgroups = mutableListOf<MutableList<ScannedVideo>>()
                    for (video in sorted) {
                        var added = false
                        for (subgroup in subgroups) {
                            if (Math.abs(video.durationMs - subgroup.first().durationMs) <= 1000) {
                                subgroup.add(video)
                                added = true
                                break
                            }
                        }
                        if (!added) {
                            subgroups.add(mutableListOf(video))
                        }
                    }
                    resultGroups.addAll(subgroups.filter { it.size > 1 })
                }
                
                if (invalidDurationVideos.size > 1) {
                    // If no duration, only group if size matches perfectly and is non-zero
                    // We already filtered for count > 1 and size > 0
                    resultGroups.add(invalidDurationVideos)
                }
                
                if (validDurationVideos.isEmpty() && invalidDurationVideos.size > 1) {
                    listOf(invalidDurationVideos)
                } else {
                    resultGroups
                }
            }
            .toList()
    }

    fun cancelScanning() {
        scanJob?.cancel()
    }

    fun removeDeletedVideosFromUI(deletedUris: List<android.net.Uri>) {
        viewModelScope.launch {
            val currentVideos = _videos.value.filterNot { it.uri in deletedUris }
            _videos.value = currentVideos
            _duplicateGroups.value = findDuplicates(currentVideos)
            _scannedCount.value = currentVideos.size
        }
    }
}
