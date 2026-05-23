package com.rp.dedup.core.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.data.ScanHistory
import com.rp.dedup.core.image.ImageHasher
import com.rp.dedup.core.image.BestShotAnalyzer
import com.rp.dedup.core.data.ScannedImage
import com.rp.dedup.core.repository.ImageScannerRepository
import com.rp.dedup.core.repository.ScanHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

class ScannerViewModel(
    private val context: Context,
    private val repository: ImageScannerRepository,
    private val historyRepository: ScanHistoryRepository? = null,
    private val dataStoreManager: com.rp.dedup.core.caching.DataStoreManager? = null,
    private val analyticsManager: AnalyticsManager? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedImage>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedImage>>> = _duplicateGroups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val allScannedGroups = mutableMapOf<Long, MutableList<ScannedImage>>()
    private val groupsLock = Any()

    private var scanJob: Job? = null
    
    private var similarityThreshold = 3 // Reduced default for more accuracy
    private var excludedFolders = emptyList<String>()

    fun startScanning() {
        if (_isScanning.value) return
        val startTime = System.currentTimeMillis()
        var wasCancelled = false
        _isScanning.value = true
        synchronized(groupsLock) {
            allScannedGroups.clear()
        }
        _duplicateGroups.value = emptyList()

        analyticsManager?.logScanStarted("IMAGE")

        scanJob = viewModelScope.launch(defaultDispatcher) {
            try {
                // Load settings before scanning
                dataStoreManager?.let { manager ->
                    val thresholdValue = manager.readData(com.rp.dedup.core.caching.DataStoreManager.SIMILARITY_THRESHOLD, "3")
                        .map { it.toIntOrNull() ?: 3 }.first()
                    similarityThreshold = thresholdValue.coerceIn(0, 10) // Cap to sane limit
                    excludedFolders = manager.readData(com.rp.dedup.core.caching.DataStoreManager.EXCLUDED_FOLDERS, "")
                        .map { if (it.isEmpty()) emptyList() else it.split(",") }.first()
                }

                val batchBuffer = mutableListOf<ScannedImage>()
                var lastUiUpdateTime = System.currentTimeMillis()

                repository.scanImagesInParallel(concurrencyLevel = 4, excludedFolders = excludedFolders).collect { newImage ->
                    batchBuffer.add(newImage)

                    if (batchBuffer.size >= 20) {
                        processBatch(batchBuffer)
                        batchBuffer.clear()

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUiUpdateTime > 500) {
                            synchronized(groupsLock) {
                                _duplicateGroups.value = allScannedGroups.values
                                    .filter { it.size > 1 }
                                    .map { it.toList() }
                            }
                            lastUiUpdateTime = currentTime
                        }
                    }
                }

                if (batchBuffer.isNotEmpty()) {
                    processBatch(batchBuffer)
                }

                // AI Post-processing: Best Shot Suggestion
                val groupsToAnalyze = synchronized(groupsLock) {
                    allScannedGroups.values
                        .filter { it.size > 1 }
                        .map { it.toList() }
                }

                val finalizedGroups = groupsToAnalyze.map { group ->
                    BestShotAnalyzer.analyzeGroup(context, group)
                }

                _duplicateGroups.value = finalizedGroups

                analyticsManager?.logScanCompleted(
                    scanType = "IMAGE",
                    totalScanned = synchronized(groupsLock) { allScannedGroups.values.sumOf { it.size } },
                    duplicatesFound = finalizedGroups.sumOf { it.size - 1 },
                    reclaimableBytes = finalizedGroups.sumOf { group -> group.drop(1).sumOf { it.sizeInBytes } }
                )

            } catch (_: CancellationException) {
                wasCancelled = true
                synchronized(groupsLock) {
                    _duplicateGroups.value = allScannedGroups.values
                        .filter { it.size > 1 }
                        .map { it.toList() }
                }
            } finally {
                _isScanning.value = false
                withContext(NonCancellable + Dispatchers.IO) {
                    saveHistory(startTime, if (wasCancelled) "CANCELLED" else "COMPLETED")
                }
            }
        }
    }

    fun cancelScanning() {
        scanJob?.cancel()
    }

    private fun processBatch(images: List<ScannedImage>) {
        synchronized(groupsLock) {
            for (image in images) {
                var foundGroup = false
                // First try exact hash match for O(1)
                val exactGroup = allScannedGroups[image.dHash]
                if (exactGroup != null) {
                    exactGroup.add(image)
                    foundGroup = true
                } else {
                    // If no exact match, try Hamming distance check for near-duplicates
                    // This is still O(Groups) but we only do it if O(1) fails
                    for (group in allScannedGroups.values) {
                        val distance =
                            ImageHasher.calculateHammingDistance(image.dHash, group.first().dHash)
                        if (distance <= similarityThreshold) {
                            group.add(image)
                            foundGroup = true
                            break
                        }
                    }
                }
                if (!foundGroup) {
                    allScannedGroups[image.dHash] = mutableListOf(image)
                }
            }
        }
    }

    private suspend fun saveHistory(startTime: Long, status: String) {
        val groups = _duplicateGroups.value
        val totalScanned = synchronized(groupsLock) { allScannedGroups.values.sumOf { it.size } }
        historyRepository?.insert(
            ScanHistory(
                scanType = "IMAGE",
                timestamp = startTime,
                durationMs = System.currentTimeMillis() - startTime,
                totalScanned = totalScanned,
                duplicateGroups = groups.size,
                totalDuplicates = groups.sumOf { it.size - 1 },
                reclaimableBytes = groups.sumOf { group -> group.drop(1).sumOf { it.sizeInBytes } },
                status = status
            )
        )
    }

    fun getAutoClearUris(): List<String> {
        val urisToDelete = mutableListOf<String>()
        _duplicateGroups.value.forEach { group ->
            if (group.size > 1) {
                for (i in 1 until group.size) {
                    urisToDelete.add(group[i].uri)
                }
            }
        }
        return urisToDelete
    }

    fun removeDeletedImagesFromUI(deletedUris: List<String>) {
        var freedBytes = 0L
        synchronized(groupsLock) {
            val iterator = allScannedGroups.values.iterator()
            while (iterator.hasNext()) {
                val group = iterator.next()
                group.filter { it.uri in deletedUris }.forEach { freedBytes += it.sizeInBytes }
                group.removeAll { it.uri in deletedUris }
                if (group.isEmpty()) {
                    iterator.remove()
                }
            }
        }
        
        analyticsManager?.logFilesDeleted("IMAGE", deletedUris.size, freedBytes)

        synchronized(groupsLock) {
            _duplicateGroups.value = allScannedGroups.values
                .filter { it.size > 1 }
                .map { it.toList() }
        }
    }
}