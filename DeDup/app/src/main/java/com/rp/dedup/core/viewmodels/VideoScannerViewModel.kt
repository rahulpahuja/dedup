package com.rp.dedup.core.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.VideoScannerRepository
import com.rp.dedup.core.model.ScanHistory
import com.rp.dedup.core.model.ScannedVideo
import com.rp.dedup.core.model.toEntity
import com.rp.dedup.core.repository.ScannedVideoRepository
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

private const val CHECKPOINT_INTERVAL = 5  // persist every N newly scanned videos

class VideoScannerViewModel(
    private val repository: VideoScannerRepository,
    private val videoRepository: ScannedVideoRepository? = null,
    private val historyRepository: ScanHistoryRepository? = null,
    private val analyticsManager: AnalyticsManager? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedVideo>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedVideo>>> = _duplicateGroups.asStateFlow()

    private val _videos = MutableStateFlow<List<ScannedVideo>>(emptyList())
    val videos: StateFlow<List<ScannedVideo>> = _videos.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedCount = MutableStateFlow(0)
    val scannedCount: StateFlow<Int> = _scannedCount.asStateFlow()

    /** True once the initial cache-load attempt has finished (whether results exist or not). */
    private val _cacheLoaded = MutableStateFlow(false)
    val cacheLoaded: StateFlow<Boolean> = _cacheLoaded.asStateFlow()

    /**
     * Number of videos that were already persisted from a previous interrupted scan.
     * Non-zero means we are resuming and should show a "Resuming" indicator.
     */
    private val _resumedCount = MutableStateFlow(0)
    val resumedCount: StateFlow<Int> = _resumedCount.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch(ioDispatcher) {
            loadCachedResults()
        }
    }

    private suspend fun loadCachedResults() {
        val repo = videoRepository ?: run { _cacheLoaded.value = true; return }
        val cachedGroups = repo.getCachedDuplicateGroups()
        if (cachedGroups.isNotEmpty()) {
            _duplicateGroups.value = cachedGroups
            _scannedCount.value = repo.getTotalScannedCount()
        }
        _cacheLoaded.value = true
    }

    /**
     * @param deepScan  whether to extract frame hashes for near-duplicate detection
     * @param forceRescan  if true, clears all cached data and starts from scratch
     */
    fun startScanning(deepScan: Boolean = true, forceRescan: Boolean = false) {
        if (_isScanning.value) return
        _isScanning.value = true   // set BEFORE launch so re-entrant calls are blocked immediately
        val startTime = System.currentTimeMillis()
        var wasCancelled = false

        scanJob = viewModelScope.launch(defaultDispatcher) {
            // ── 1. Optionally wipe cache for a full rescan ──────────────────
            if (forceRescan) {
                withContext(ioDispatcher) { videoRepository?.clearAll() }
                _videos.value = emptyList()
                _duplicateGroups.value = emptyList()
                _scannedCount.value = 0
                _resumedCount.value = 0
            }

            // ── 2. Load already-scanned URIs so we can skip them ───────────
            val alreadyScannedUris: Set<Uri> = withContext(ioDispatcher) {
                videoRepository?.getScannedUris() ?: emptySet()
            }
            _resumedCount.value = alreadyScannedUris.size

            analyticsManager?.logScanStarted("VIDEO")

            // ── 3. Pre-populate in-memory structures from cached groups ─────
            val allVideos = mutableListOf<ScannedVideo>()
            val sizeIndex = mutableMapOf<Long, MutableList<ScannedVideo>>()
            val frameHashVideos = mutableListOf<ScannedVideo>()
            val uriToGroupIdx = mutableMapOf<Uri, Int>()
            val runningGroups = mutableListOf<MutableList<ScannedVideo>>()

            // Rebuild the in-memory dedup structures from cached results so
            // newly scanned videos can be merged into existing groups correctly.
            _duplicateGroups.value.forEach { group ->
                val groupIdx = runningGroups.size
                runningGroups.add(group.toMutableList())
                group.forEach { v ->
                    uriToGroupIdx[v.uri] = groupIdx
                    sizeIndex.getOrPut(v.sizeInBytes) { mutableListOf() }.add(v)
                    if (v.frameHashes.isNotEmpty()) frameHashVideos.add(v)
                    allVideos.add(v)
                }
            }
            _scannedCount.value = allVideos.size

            try {
                var checkpointCounter = 0

                repository.scanVideos(deepScan = deepScan)
                    .buffer(capacity = Channel.BUFFERED)
                    .collect { video ->
                        // Skip videos processed in a previous session
                        if (video.uri in alreadyScannedUris) return@collect

                        allVideos.add(video)
                        _scannedCount.value = allVideos.size

                        addVideoIncrementally(video, sizeIndex, frameHashVideos, uriToGroupIdx, runningGroups)
                        _duplicateGroups.value = runningGroups.filter { it.size > 1 }.map { it.toList() }

                        if (allVideos.size % 10 == 0) {
                            _videos.value = allVideos.toList()
                        }

                        // ── Checkpoint: persist progress so resume is possible ──
                        checkpointCounter++
                        if (checkpointCounter >= CHECKPOINT_INTERVAL) {
                            checkpointCounter = 0
                            persistCheckpoint(allVideos, uriToGroupIdx, runningGroups)
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
                analyticsManager?.logScanCancelled("VIDEO")
                _videos.value = allVideos.toList()
            } finally {
                _isScanning.value = false

                withContext(NonCancellable + ioDispatcher) {
                    // Final persist — captures complete group assignments
                    persistCheckpoint(allVideos, uriToGroupIdx, runningGroups)

                    val groups = _duplicateGroups.value
                    historyRepository?.insert(
                        ScanHistory(
                            scanType = "VIDEO",
                            timestamp = startTime,
                            durationMs = System.currentTimeMillis() - startTime,
                            totalScanned = _scannedCount.value,
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

    /** Clears the persisted cache and resets in-memory state. */
    fun clearCache() {
        viewModelScope.launch(ioDispatcher) {
            videoRepository?.clearAll()
            withContext(Dispatchers.Main.immediate) {
                _videos.value = emptyList()
                _duplicateGroups.value = emptyList()
                _scannedCount.value = 0
                _resumedCount.value = 0
                _cacheLoaded.value = true
            }
        }
    }

    // ── Persistence helpers ─────────────────────────────────────────────────

    private suspend fun persistCheckpoint(
        allVideos: List<ScannedVideo>,
        uriToGroupIdx: Map<Uri, Int>,
        groups: List<MutableList<ScannedVideo>>
    ) = withContext(ioDispatcher) {
        val entities = allVideos.map { video ->
            val groupKey = deriveGroupKey(video, uriToGroupIdx, groups)
            video.toEntity(groupKey)
        }
        videoRepository?.insertVideos(entities)
    }

    /**
     * Derives a stable, content-based group key so that re-loading from DB
     * produces the same grouping without re-running the dedup algorithm.
     */
    private fun deriveGroupKey(
        video: ScannedVideo,
        uriToGroupIdx: Map<Uri, Int>,
        groups: List<MutableList<ScannedVideo>>
    ): String {
        val idx = uriToGroupIdx[video.uri] ?: return ""
        val group = groups.getOrNull(idx) ?: return ""
        if (group.size < 2) return ""
        val rep = group.first()
        return "s:${rep.sizeInBytes}_${rep.frameHashes.firstOrNull() ?: 0}"
    }

    // ── Incremental dedup logic ─────────────────────────────────────────────

    private fun addVideoIncrementally(
        video: ScannedVideo,
        sizeIndex: MutableMap<Long, MutableList<ScannedVideo>>,
        frameHashVideos: MutableList<ScannedVideo>,
        uriToGroupIdx: MutableMap<Uri, Int>,
        groups: MutableList<MutableList<ScannedVideo>>
    ) {
        var joinedIdx: Int? = null

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

    // ── Public API ──────────────────────────────────────────────────────────

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

            // Remove from persisted cache too
            withContext(ioDispatcher) {
                deletedUris.forEach { videoRepository?.deleteByUri(it.toString()) }
            }

            analyticsManager?.logFilesDeleted("VIDEO", deletedUris.size, freedBytes)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            return VideoScannerViewModel(
                repository = VideoScannerRepository(context),
                videoRepository = ScannedVideoRepository(db.scannedVideoDao()),
                historyRepository = ScanHistoryRepository(db.scanHistoryDao()),
                analyticsManager = AnalyticsManager.getInstance(context)
            ) as T
        }
    }
}
