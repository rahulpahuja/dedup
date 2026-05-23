package com.rp.dedup.core.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.repository.VideoScannerRepository
import com.rp.dedup.core.data.ScanHistory
import com.rp.dedup.core.data.ScannedVideo
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.image.ImageHasher
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

    fun startScanning(deepScan: Boolean = true) {
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
                repository.scanVideos(deepScan = deepScan)
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
        val resultGroups = mutableListOf<MutableList<ScannedVideo>>()
        val processed = mutableSetOf<Int>()

        for (i in allVideos.indices) {
            if (i in processed) continue
            val group = mutableListOf(allVideos[i])
            
            for (j in i + 1 until allVideos.size) {
                if (j in processed) continue
                
                val v1 = allVideos[i]
                val v2 = allVideos[j]
                
                var isMatch = false
                
                // 1. Check exact size match
                if (v1.sizeInBytes == v2.sizeInBytes && v1.sizeInBytes > 0) {
                    isMatch = true
                } 
                // 2. Check content-based match (frame hashes)
                else if (v1.frameHashes.isNotEmpty() && v2.frameHashes.isNotEmpty()) {
                    // Compare hashes at 10%, 50%, 90%
                    var matchCount = 0
                    for (h1 in v1.frameHashes) {
                        for (h2 in v2.frameHashes) {
                            if (ImageHasher.calculateHammingDistance(h1, h2) <= 3) {
                                matchCount++
                                break
                            }
                        }
                    }
                    // If at least 2 out of 3 frames match, consider it a duplicate content
                    if (matchCount >= 2) {
                        isMatch = true
                    }
                }
                
                if (isMatch) {
                    group.add(v2)
                    processed.add(j)
                }
            }
            
            if (group.size > 1) {
                resultGroups.add(group)
            }
            processed.add(i)
        }
        
        return resultGroups
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
