package com.rp.dedup.core.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

class ScannerViewModel(private val repository: ImageScannerRepository) : ViewModel() {

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedImage>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedImage>>> = _duplicateGroups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val allScannedGroups = mutableListOf<MutableList<ScannedImage>>()

    // --- NEW: Track the active scanning job ---
    private var scanJob: Job? = null

    fun startScanning() {
        // Assign the launch block to our scanJob variable
        scanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                _isScanning.value = true
                allScannedGroups.clear()
                _duplicateGroups.value = emptyList()

                val batchBuffer = mutableListOf<ScannedImage>()
                var lastUiUpdateTime = System.currentTimeMillis()

                repository.scanImagesInParallel(concurrencyLevel = 4).collect { newImage ->
                    batchBuffer.add(newImage)

                    if (batchBuffer.size >= 20) {
                        processBatch(batchBuffer)
                        batchBuffer.clear()

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUiUpdateTime > 500) {
                            _duplicateGroups.value = allScannedGroups
                                .filter { it.size > 1 }
                                .map { it.toList() }
                            lastUiUpdateTime = currentTime
                        }
                    }
                }

                if (batchBuffer.isNotEmpty()) {
                    processBatch(batchBuffer)
                }

                _duplicateGroups.value = allScannedGroups
                    .filter { it.size > 1 }
                    .map { it.toList() }

            } catch (_: CancellationException) {
                // This is completely normal! It just means the user hit cancel.
                // We catch it so we can push whatever duplicates we found BEFORE they hit cancel.
                _duplicateGroups.value = allScannedGroups
                    .filter { it.size > 1 }
                    .map { it.toList() }
            } finally {
                // Whether it finishes naturally or gets cancelled, always turn off the loading spinner
                _isScanning.value = false
            }
        }
    }

    // --- NEW: Cancel Function ---
    fun cancelScanning() {
        scanJob?.cancel()
    }

    private fun processBatch(images: List<ScannedImage>) {
        for (image in images) {
            var foundGroup = false
            for (group in allScannedGroups) {
                val distance = ImageHasher.calculateHammingDistance(image.dHash, group.first().dHash)
                if (distance <= 5) {
                    group.add(image)
                    foundGroup = true
                    break
                }
            }
            if (!foundGroup) {
                allScannedGroups.add(mutableListOf(image))
            }
        }
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
        allScannedGroups.forEach { group ->
            group.removeAll { it.uri in deletedUris }
        }
        _duplicateGroups.value = allScannedGroups
            .filter { it.size > 1 }
            .map { it.toList() }
    }
}