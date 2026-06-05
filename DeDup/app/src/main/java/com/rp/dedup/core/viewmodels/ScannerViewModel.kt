package com.rp.dedup.core.viewmodels

import android.content.Context
import android.provider.MediaStore
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.model.ScanHistory
import com.rp.dedup.core.image.ImageHasher
import com.rp.dedup.core.image.BestShotAnalyzer
import com.rp.dedup.core.model.ScannedImage
import com.rp.dedup.core.repository.ImageScannerRepository
import com.rp.dedup.core.repository.ScannedImageRepository
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
    private val scannedImageRepository: ScannedImageRepository? = null,
    private val dataStoreManager: com.rp.dedup.core.caching.DataStoreManager? = null,
    private val analyticsManager: AnalyticsManager? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedImage>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedImage>>> = _duplicateGroups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isStale = MutableStateFlow(false)
    val isStale: StateFlow<Boolean> = _isStale.asStateFlow()

    /** True once the initial cache load attempt has completed (whether results exist or not). */
    private val _cacheLoaded = MutableStateFlow(false)
    val cacheLoaded: StateFlow<Boolean> = _cacheLoaded.asStateFlow()

    // Key format: "e:<crc32>" for exact-byte duplicates, "d:<dHash>" for near-duplicates.
    private val allScannedGroups = mutableMapOf<String, MutableList<ScannedImage>>()
    private val groupsLock = Any()

    private var scanJob: Job? = null

    private var similarityThreshold = 3
    private var excludedFolders = emptyList<String>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadCachedResults()
        }
    }

    private suspend fun loadCachedResults() {
        val cached = scannedImageRepository?.getCachedDuplicateGroups() ?: run {
            _cacheLoaded.value = true
            return
        }
        if (cached.isNotEmpty()) {
            _duplicateGroups.value = cached
            checkStaleness()
        }
        _cacheLoaded.value = true
    }

    private suspend fun checkStaleness() {
        val lastScanMs = dataStoreManager
            ?.readData(com.rp.dedup.core.caching.DataStoreManager.LAST_IMAGE_SCAN_TIME, "0")
            ?.map { it.toLongOrNull() ?: 0L }
            ?.first() ?: return
        if (lastScanMs == 0L) {
            _isStale.value = true
            return
        }
        // MediaStore DATE_MODIFIED is in seconds; lastScanMs is milliseconds.
        val maxModifiedSec = queryMaxMediaStoreModified()
        _isStale.value = maxModifiedSec * 1000L > lastScanMs
    }

    private fun queryMaxMediaStoreModified(): Long {
        val projection = arrayOf("MAX(${MediaStore.Images.Media.DATE_MODIFIED})")
        return context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L
    }

    fun startScanning() {
        if (_isScanning.value) return
        val startTime = System.currentTimeMillis()
        var wasCancelled = false
        _isScanning.value = true
        synchronized(groupsLock) { allScannedGroups.clear() }
        _duplicateGroups.value = emptyList()

        analyticsManager?.logScanStarted("IMAGE")

        scanJob = viewModelScope.launch(defaultDispatcher) {
            try {
                dataStoreManager?.let { manager ->
                    val thresholdValue = manager.readData(com.rp.dedup.core.caching.DataStoreManager.SIMILARITY_THRESHOLD, "3")
                        .map { it.toIntOrNull() ?: 3 }.first()
                    similarityThreshold = thresholdValue.coerceIn(0, 10)
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

                val groupsToAnalyze = synchronized(groupsLock) {
                    allScannedGroups.values
                        .filter { it.size > 1 }
                        .map { it.toList() }
                }

                val finalizedGroups = BestShotAnalyzer.analyzeGroups(context, groupsToAnalyze)

                _duplicateGroups.value = finalizedGroups

                val totalScanned = synchronized(groupsLock) { allScannedGroups.values.sumOf { it.size } }
                val duplicatesFound = finalizedGroups.sumOf { it.size - 1 }
                val reclaimableBytes = finalizedGroups.sumOf { group -> group.drop(1).sumOf { it.sizeInBytes } }

                analyticsManager?.logScanCompleted(
                    scanType = "IMAGE",
                    totalScanned = totalScanned,
                    duplicatesFound = duplicatesFound,
                    reclaimableBytes = reclaimableBytes
                )

                // Android 17 Handoff: Broadcast state to other devices
                if (android.os.Build.VERSION.SDK_INT >= 37) {
                    val companionManager = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
                    companionManager?.let {
                        val handoffBundle = Bundle().apply {
                            putString("handoff_route", "image_scanner")
                            putString("scan_type", "IMAGE")
                            putInt("duplicates_found", duplicatesFound)
                        }
                        // This is a simplified representation of the Android 17 Handoff API
                        // In a real implementation, you'd use startHandoff() with a ClipData or specific intent
                        val handoffIntent = Intent("android.intent.action.HANDOFF_RECEIVED").apply {
                            putExtras(handoffBundle)
                        }
                        context.sendBroadcast(handoffIntent)
                    }
                }

                persistResults(finalizedGroups)

            } catch (e: Exception) {
                if (e is CancellationException) {
                    wasCancelled = true
                    analyticsManager?.logScanCancelled("IMAGE")
                } else {
                    analyticsManager?.logError("IMAGE", e.message ?: "Unknown Error")
                }
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

    private fun persistResults(finalizedGroups: List<List<ScannedImage>>) {
        viewModelScope.launch(Dispatchers.IO) {
            scannedImageRepository?.clearAll()
            val imagesToPersist = finalizedGroups.flatMap { group ->
                // Derive the same stable key that the scanner uses so reloaded groups
                // are compatible with any future delta-scan logic.
                val key = if (group.first().exactHash != -1L)
                    "e:${group.first().exactHash}"
                else
                    "d:${group.first().dHash}"
                group.map { it.copy(groupKey = key) }
            }
            scannedImageRepository?.insertImages(imagesToPersist)
            dataStoreManager?.writeData(
                com.rp.dedup.core.caching.DataStoreManager.LAST_IMAGE_SCAN_TIME,
                System.currentTimeMillis().toString()
            )
            _isStale.value = false
        }
    }

    fun cancelScanning() {
        scanJob?.cancel()
    }

    private fun processBatch(images: List<ScannedImage>) {
        synchronized(groupsLock) {
            for (image in images) {
                var groupKey: String? = null

                if (image.exactHash != -1L) {
                    val key = "e:${image.exactHash}"
                    if (allScannedGroups.containsKey(key)) groupKey = key
                }

                if (groupKey == null) {
                    for ((key, group) in allScannedGroups) {
                        if (ImageHasher.calculateHammingDistance(
                                image.dHash, group.first().dHash
                            ) <= similarityThreshold
                        ) {
                            groupKey = key
                            break
                        }
                    }
                }

                if (groupKey != null) {
                    allScannedGroups[groupKey]!!.add(image)
                } else {
                    val newKey = if (image.exactHash != -1L) "e:${image.exactHash}" else "d:${image.dHash}"
                    allScannedGroups[newKey] = mutableListOf(image)
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
                if (group.isEmpty()) iterator.remove()
            }
        }

        analyticsManager?.logFilesDeleted("IMAGE", deletedUris.size, freedBytes)

        synchronized(groupsLock) {
            _duplicateGroups.value = allScannedGroups.values
                .filter { it.size > 1 }
                .map { it.toList() }
        }

        viewModelScope.launch(Dispatchers.IO) {
            deletedUris.forEach { scannedImageRepository?.deleteByUri(it) }
        }
    }
}
