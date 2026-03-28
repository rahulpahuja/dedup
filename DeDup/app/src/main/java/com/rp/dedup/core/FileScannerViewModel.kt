package com.rp.dedup.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileScannerViewModel(private val repository: FileScannerRepository) : ViewModel() {

    private val _files = MutableStateFlow<List<ScannedFile>>(emptyList())
    val files: StateFlow<List<ScannedFile>> = _files.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<List<ScannedFile>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<ScannedFile>>> = _duplicateGroups.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    fun startScanning(extensions: List<String>) {
        if (_isScanning.value) return
        _isScanning.value = true
        _files.value = emptyList()
        _duplicateGroups.value = emptyList()

        scanJob = viewModelScope.launch {
            val allFiles = mutableListOf<ScannedFile>()
            repository.scanFilesByExtension(extensions).collect { file ->
                allFiles.add(file)
            }
            
            _files.value = allFiles
            findDuplicates(allFiles)
            _isScanning.value = false
        }
    }

    private fun findDuplicates(allFiles: List<ScannedFile>) {
        // Group by size first as a quick filter
        val sizeGroups = allFiles.groupBy { it.size }.filter { it.value.size > 1 }
        
        val duplicates = mutableListOf<List<ScannedFile>>()
        
        sizeGroups.forEach { (_, filesWithSameSize) ->
            // In a real app, you'd check MD5/SHA here. 
            // For now, we'll group by name + size as a heuristic for "duplicates"
            val nameGroups = filesWithSameSize.groupBy { it.name }.filter { it.value.size > 1 }
            nameGroups.values.forEach { duplicates.add(it) }
        }
        
        _duplicateGroups.value = duplicates
    }

    fun cancelScanning() {
        scanJob?.cancel()
        _isScanning.value = false
    }
}
