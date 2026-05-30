package com.rp.dedup.core.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.model.ScannedFile
import com.rp.dedup.core.repository.FileScannerRepository
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.model.ScanHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

class FileScannerViewModel(
    private val repository: FileScannerRepository,
    private val historyRepository: ScanHistoryRepository? = null,
    private val scanTypeName: String = "FILE"
) : ViewModel() {

    private val _files = MutableStateFlow<List<ScannedFile>>(emptyList())
    val files: StateFlow<List<ScannedFile>> = _files.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedFile>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedFile>>> = _duplicateGroups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScanning(extensions: List<String>) {
        if (_isScanning.value) return
        val startTime = System.currentTimeMillis()
        var wasCancelled = false
        _isScanning.value = true
        _files.value = emptyList()
        _duplicateGroups.value = emptyList()

        scanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val allFiles = mutableListOf<ScannedFile>()
                repository.scanFilesByExtension(extensions).collect { file ->
                    allFiles.add(file)
                    if (allFiles.size % 10 == 0) {
                        _files.value = allFiles.toList()
                    }
                }

                _files.value = allFiles.toList()
                findDuplicates(allFiles)
            } catch (_: CancellationException) {
                wasCancelled = true
            } finally {
                _isScanning.value = false
                withContext(NonCancellable + Dispatchers.IO) {
                    val groups = _duplicateGroups.value
                    historyRepository?.insert(
                        ScanHistory(
                            scanType = scanTypeName,
                            timestamp = startTime,
                            durationMs = System.currentTimeMillis() - startTime,
                            totalScanned = _files.value.size,
                            duplicateGroups = groups.size,
                            totalDuplicates = groups.sumOf { it.size - 1 },
                            reclaimableBytes = groups.sumOf { group ->
                                group.drop(1).sumOf { it.size }
                            },
                            status = if (wasCancelled) "CANCELLED" else "COMPLETED"
                        )
                    )
                }
            }
        }
    }

    private fun findDuplicates(allFiles: List<ScannedFile>) {
        val duplicates = mutableListOf<List<ScannedFile>>()

        // First, group by size to find potential candidates
        val sizeGroups = allFiles.groupBy { it.size }.filter { it.value.size > 1 }

        sizeGroups.forEach { (_, filesWithSameSize) ->
            // If checksum is present (Deep Scan), use it. Otherwise, fallback to name.
            if (filesWithSameSize.firstOrNull()?.checksum != null) {
                val checksumGroups = filesWithSameSize.groupBy { it.checksum }.filter { it.value.size > 1 }
                checksumGroups.values.forEach { duplicates.add(it) }
            } else {
                val nameGroups = filesWithSameSize.groupBy { it.name }.filter { it.value.size > 1 }
                nameGroups.values.forEach { duplicates.add(it) }
            }
        }

        _duplicateGroups.value = duplicates
    }

    fun cancelScanning() {
        scanJob?.cancel()
    }

    fun removeDeletedFilesFromUI(deletedUris: List<android.net.Uri>) {
        viewModelScope.launch {
            val currentFiles = _files.value.filterNot { it.uri in deletedUris }
            _files.value = currentFiles
            findDuplicates(currentFiles)
        }
    }
}
