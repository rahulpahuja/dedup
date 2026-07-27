package com.rp.dedup.core.viewmodels

import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.db.AppDatabase
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
    context: Context,
    private val repository: ImageScannerRepository,
    private val historyRepository: ScanHistoryRepository? = null,
    private val scannedImageRepository: ScannedImageRepository? = null,
    private val dataStoreManager: com.rp.dedup.core.caching.DataStoreManager? = null,
    private val analyticsManager: AnalyticsManager? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val context: Context = context.applicationContext

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedImage>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedImage>>> = _duplicateGroups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isStale = MutableStateFlow(false)
    val isStale: StateFlow<Boolean> = _isStale.asStateFlow()

    /** True once the initial cache load attempt has completed (whether results exist or not). */
    private val _cacheLoaded = MutableStateFlow(false)
    val cacheLoaded: StateFlow<Boolean> = _cacheLoaded.asStateFlow()

    // Key format: "e:<sizeBytes>_<crc32>" for exact-byte duplicates, "d:<dHash>" for near-duplicates.
    private val allScannedGroups = mutableMapOf<String, MutableList<ScannedImage>>()
    private val groupsLock = Any()

    private var scanJob: Job? = null

    private var similarityThreshold = 6
    private var excludedFolders = emptyList<String>()

    init {
        viewModelScope.launch(ioDispatcher) {
            loadCachedResults()
        }
    }

    private suspend fun loadCachedResults() {
        val cached = scannedImageRepository?.getCachedDuplicateGroups()
        val isStale = if (!cached.isNullOrEmpty()) computeStaleness() else false
        withContext(Dispatchers.Main.immediate) {
            if (!cached.isNullOrEmpty()) {
                _duplicateGroups.value = cached
                _isStale.value = isStale
            }
            _cacheLoaded.value = true
        }
    }

    private suspend fun computeStaleness(): Boolean {
        val lastScanMs = dataStoreManager
            ?.readData(com.rp.dedup.core.caching.DataStoreManager.LAST_IMAGE_SCAN_TIME, "0")
            ?.map { it.toLongOrNull() ?: 0L }
            ?.first() ?: return false
        if (lastScanMs == 0L) return true
        // MediaStore DATE_MODIFIED is in seconds; lastScanMs is milliseconds.
        val maxModifiedSec = queryMaxMediaStoreModified()
        return maxModifiedSec * 1000L > lastScanMs
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
                    val thresholdValue = manager.readData(com.rp.dedup.core.caching.DataStoreManager.SIMILARITY_THRESHOLD, "10")
                        .map { it.toIntOrNull() ?: 10 }.first()
                    similarityThreshold = thresholdValue.coerceIn(0, 20)
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

                withContext(NonCancellable + ioDispatcher) {
                    persistResults(finalizedGroups)
                }

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
                withContext(NonCancellable + ioDispatcher) {
                    saveHistory(startTime, if (wasCancelled) "CANCELLED" else "COMPLETED")
                }
            }
        }
    }

    private suspend fun persistResults(finalizedGroups: List<List<ScannedImage>>) {
        try {
            scannedImageRepository?.clearAll()
            val imagesToPersist = finalizedGroups.flatMap { group ->
                val representative = group.first()
                val key = if (representative.exactHash != -1L)
                    "e:${representative.sizeInBytes}_${representative.exactHash}"
                else
                    "d:${representative.dHash}"
                group.map { it.copy(groupKey = key) }
            }
            scannedImageRepository?.insertImages(imagesToPersist)
            dataStoreManager?.writeData(
                com.rp.dedup.core.caching.DataStoreManager.LAST_IMAGE_SCAN_TIME,
                System.currentTimeMillis().toString()
            )
            withContext(Dispatchers.Main.immediate) { _isStale.value = false }
        } catch (e: Exception) {
            android.util.Log.e("ScannerViewModel", "Failed to persist scan results", e)
        }
    }

    fun cancelScanning() {
        scanJob?.cancel()
    }

    private fun processBatch(images: List<ScannedImage>) {
        synchronized(groupsLock) {
            for (image in images) {
                // Stage 1: exact-byte match — requires both same size AND same CRC32 prefix.
                // Including sizeInBytes in the key prevents 32-bit CRC32 collisions across
                // files of different sizes, which caused false-positive duplicate groups.
                if (image.exactHash != -1L) {
                    val exactKey = "e:${image.sizeInBytes}_${image.exactHash}"
                    if (allScannedGroups.containsKey(exactKey)) {
                        allScannedGroups[exactKey]!!.add(image)
                        continue
                    }
                }

                // Stage 2: near-duplicate match via dHash — only scan "d:" groups.
                // Never expand "e:" groups via dHash: those groups represent byte-identical files
                // and adding a merely similar image would corrupt the exact-duplicate semantics.
                var nearDupKey: String? = null
                for ((key, group) in allScannedGroups) {
                    if (key.startsWith("e:")) continue
                    if (ImageHasher.calculateHammingDistance(
                            image.dHash, group.first().dHash
                        ) <= similarityThreshold
                    ) {
                        nearDupKey = key
                        break
                    }
                }

                if (nearDupKey != null) {
                    allScannedGroups[nearDupKey]!!.add(image)
                } else {
                    // New group. Use exact key when available so future byte-identical
                    // images can find it in Stage 1; otherwise use dHash key for near-dups.
                    val newKey = if (image.exactHash != -1L)
                        "e:${image.sizeInBytes}_${image.exactHash}"
                    else
                        "d:${image.dHash}"
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

    override fun onCleared() {
        super.onCleared()
        BestShotAnalyzer.close()
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

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val db = AppDatabase.getDatabase(appContext)
            return ScannerViewModel(
                context = appContext,
                repository = ImageScannerRepository(appContext),
                historyRepository = ScanHistoryRepository(db.scanHistoryDao()),
                scannedImageRepository = ScannedImageRepository(db.scannedImageDao()),
                dataStoreManager = DataStoreManager(appContext),
                analyticsManager = AnalyticsManager.getInstance(appContext)
            ) as T
        }
    }
}
