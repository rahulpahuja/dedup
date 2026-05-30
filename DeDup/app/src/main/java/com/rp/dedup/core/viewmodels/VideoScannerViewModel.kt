package com.rp.dedup.core.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.repository.VideoScannerRepository
import com.rp.dedup.core.model.ScanHistory
import com.rp.dedup.core.model.ScannedVideo
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.analytics.AnalyticsManager
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
    private val analyticsManager: AnalyticsManager? = null,
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

        analyticsManager?.logScanStarted("VIDEO")

        scanJob = viewModelScope.launch(defaultDispatcher) {
            val allVideos = mutableListOf<ScannedVideo>()
            // Incremental state
            val sizeIndex = mutableMapOf<Long, MutableList<ScannedVideo>>()
            val frameHashVideos = mutableListOf<ScannedVideo>()
            val uriToGroupIdx = mutableMapOf<Uri, Int>()
            val runningGroups = mutableListOf<MutableList<ScannedVideo>>()

            try {
                repository.scanVideos(deepScan = deepScan)
                    .buffer(capacity = Channel.BUFFERED)
                    .collect { video ->
                        allVideos.add(video)
                        _scannedCount.value = allVideos.size

                        addVideoIncrementally(video, sizeIndex, frameHashVideos, uriToGroupIdx, runningGroups)
                        _duplicateGroups.value = runningGroups.filter { it.size > 1 }.map { it.toList() }

                        if (allVideos.size % 10 == 0) {
                            _videos.value = allVideos.toList()
                        }
                    }

                _videos.value = allVideos.toList()

                val groups = _duplicateGroups.value
                analyticsManager?.logScanCompleted(
                    scanType = "VIDEO",
                    totalScanned = allVideos.size,
                    duplicatesFound = groups.sumOf { it.size - 1 },
                    reclaimableBytes = groups.sumOf { group -> group.drop(1).sumOf { it.sizeInBytes } }
                )

            } catch (_: CancellationException) {
                wasCancelled = true
                _videos.value = allVideos.toList()
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

    // Checks the new video against already-seen videos and merges it into a group on match.
    private fun addVideoIncrementally(
        video: ScannedVideo,
        sizeIndex: MutableMap<Long, MutableList<ScannedVideo>>,
        frameHashVideos: MutableList<ScannedVideo>,
        uriToGroupIdx: MutableMap<Uri, Int>,
        groups: MutableList<MutableList<ScannedVideo>>
    ) {
        var joinedIdx: Int? = null

        // Phase 1: exact size match
        if (video.sizeInBytes > 0) {
            sizeIndex[video.sizeInBytes]?.forEach { existing ->
                val existIdx = uriToGroupIdx[existing.uri]
                when {
                    joinedIdx == null && existIdx == null -> {
                        val g = mutableListOf(existing, video)
                        groups.add(g)
                        joinedIdx = groups.lastIndex
                        uriToGroupIdx[existing.uri] = joinedIdx!!
                        uriToGroupIdx[video.uri] = joinedIdx!!
                    }
                    joinedIdx == null -> {
                        joinedIdx = existIdx!!
                        groups[joinedIdx!!].add(video)
                        uriToGroupIdx[video.uri] = joinedIdx!!
                    }
                    existIdx == null -> {
                        groups[joinedIdx!!].add(existing)
                        uriToGroupIdx[existing.uri] = joinedIdx!!
                    }
                }
            }
        }

        // Phase 2: frame hash similarity match (only when no exact size match)
        if (joinedIdx == null && video.frameHashes.isNotEmpty()) {
            for (existing in frameHashVideos) {
                if (!isFrameSimilar(video, existing)) continue
                val existIdx = uriToGroupIdx[existing.uri]
                when {
                    joinedIdx == null && existIdx == null -> {
                        val g = mutableListOf(existing, video)
                        groups.add(g)
                        joinedIdx = groups.lastIndex
                        uriToGroupIdx[existing.uri] = joinedIdx!!
                        uriToGroupIdx[video.uri] = joinedIdx!!
                    }
                    joinedIdx == null -> {
                        joinedIdx = existIdx!!
                        groups[joinedIdx!!].add(video)
                        uriToGroupIdx[video.uri] = joinedIdx!!
                    }
                    existIdx == null -> {
                        groups[joinedIdx!!].add(existing)
                        uriToGroupIdx[existing.uri] = joinedIdx!!
                    }
                }
            }
        }

        sizeIndex.getOrPut(video.sizeInBytes) { mutableListOf() }.add(video)
        if (video.frameHashes.isNotEmpty()) frameHashVideos.add(video)
    }

    private fun isFrameSimilar(v1: ScannedVideo, v2: ScannedVideo): Boolean {
        var matchCount = 0
        for (h1 in v1.frameHashes) {
            for (h2 in v2.frameHashes) {
                if (ImageHasher.calculateHammingDistance(h1, h2) <= 3) {
                    matchCount++
                    break
                }
            }
        }
        return matchCount >= 2
    }

    // Used only by removeDeletedVideosFromUI — full re-check on the remaining set.
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
                val isMatch = when {
                    v1.sizeInBytes == v2.sizeInBytes && v1.sizeInBytes > 0 -> true
                    v1.frameHashes.isNotEmpty() && v2.frameHashes.isNotEmpty() -> isFrameSimilar(v1, v2)
                    else -> false
                }
                if (isMatch) {
                    group.add(v2)
                    processed.add(j)
                }
            }

            if (group.size > 1) resultGroups.add(group)
            processed.add(i)
        }

        return resultGroups
    }

    fun cancelScanning() {
        scanJob?.cancel()
    }

    fun removeDeletedVideosFromUI(deletedUris: List<Uri>) {
        viewModelScope.launch {
            val toDelete = _videos.value.filter { it.uri in deletedUris }
            val freedBytes = toDelete.sumOf { it.sizeInBytes }

            val currentVideos = _videos.value.filterNot { it.uri in deletedUris }
            _videos.value = currentVideos
            _duplicateGroups.value = findDuplicates(currentVideos)
            _scannedCount.value = currentVideos.size

            analyticsManager?.logFilesDeleted("VIDEO", deletedUris.size, freedBytes)
        }
    }
}
