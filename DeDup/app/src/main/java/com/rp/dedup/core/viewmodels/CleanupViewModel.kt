package com.rp.dedup.core.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.os.Environment
import com.rp.dedup.core.model.CleanupCategoryStats
import com.rp.dedup.core.model.CleanupScreenState
import com.rp.dedup.core.model.ScannedFile
import com.rp.dedup.core.repository.FileScannerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CleanupViewModel(private val repository: FileScannerRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanupScreenState())
    val uiState: StateFlow<CleanupScreenState> = _uiState.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        scanVideos()
        scanArchives()
        scanAppDownloads()
        scanOldDownloads()
    }

    private fun scanVideos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(videoStats = _uiState.value.videoStats.copy(isLoading = true))
            val files = mutableListOf<ScannedFile>()
            // Videos are usually large; let's look for common extensions
            repository.scanFilesByExtension(listOf("mp4", "mkv", "mov", "avi")).collect { file ->
                // Heuristic for "Large/Unused": > 10MB (Lowered from 50MB)
                if (file.size > 10 * 1024 * 1024) {
                    files.add(file)
                }
            }
            _uiState.value = _uiState.value.copy(
                videoStats = CleanupCategoryStats(
                    totalSize = files.sumOf { it.size },
                    count = files.size,
                    files = files,
                    isLoading = false
                )
            )
        }
    }

    private fun scanArchives() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(archiveStats = _uiState.value.archiveStats.copy(isLoading = true))
            val files = mutableListOf<ScannedFile>()
            repository.scanFilesByExtension(listOf("zip", "rar", "7z", "tar", "gz")).collect { file ->
                files.add(file)
            }
            _uiState.value = _uiState.value.copy(
                archiveStats = CleanupCategoryStats(
                    totalSize = files.sumOf { it.size },
                    count = files.size,
                    files = files,
                    isLoading = false
                )
            )
        }
    }

    private fun scanAppDownloads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(appDownloadStats = _uiState.value.appDownloadStats.copy(isLoading = true))
            val files = mutableListOf<ScannedFile>()
            repository.scanFilesByExtension(listOf("apk", "obb")).collect { file ->
                if (file.size > 1 * 1024 * 1024) { // > 1MB (Lowered from 5MB)
                    files.add(file)
                }
            }
            _uiState.value = _uiState.value.copy(
                appDownloadStats = CleanupCategoryStats(
                    totalSize = files.sumOf { it.size },
                    count = files.size,
                    files = files,
                    isLoading = false
                )
            )
        }
    }

    private fun scanOldDownloads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(oldDownloadStats = _uiState.value.oldDownloadStats.copy(isLoading = true))
            val files = mutableListOf<ScannedFile>()
            val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            val threeMonthsAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
            
            repository.scanOldFiles(downloadsFolder, threeMonthsAgo).collect { file ->
                files.add(file)
            }
            
            _uiState.value = _uiState.value.copy(
                oldDownloadStats = CleanupCategoryStats(
                    totalSize = files.sumOf { it.size },
                    count = files.size,
                    files = files,
                    isLoading = false
                )
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val repository: FileScannerRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CleanupViewModel(repository) as T
        }
    }
}
