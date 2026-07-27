package com.rp.dedup.core.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.os.Environment
import com.rp.dedup.core.model.CleanupCategoryStats
import com.rp.dedup.core.model.state.CleanupScreenState
import com.rp.dedup.core.model.ScannedFile
import com.rp.dedup.core.repository.FileScannerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CleanupViewModel(private val repository: FileScannerRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanupScreenState())
    val uiState: StateFlow<CleanupScreenState> = _uiState.asStateFlow()

    // Serialises read-modify-write updates so concurrent category scans don't
    // overwrite each other's results.
    private val stateMutex = Mutex()

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
            stateMutex.withLock {
                _uiState.value = _uiState.value.copy(
                    videoStats = _uiState.value.videoStats.copy(isLoading = true)
                )
            }
            val files = mutableListOf<ScannedFile>()
            repository.scanFilesByExtension(listOf("mp4", "mkv", "mov", "avi")).collect { file ->
                if (file.size > 10 * 1024 * 1024) files.add(file)
            }
            stateMutex.withLock {
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
    }

    private fun scanArchives() {
        viewModelScope.launch {
            stateMutex.withLock {
                _uiState.value = _uiState.value.copy(
                    archiveStats = _uiState.value.archiveStats.copy(isLoading = true)
                )
            }
            val files = mutableListOf<ScannedFile>()
            repository.scanFilesByExtension(listOf("zip", "rar", "7z", "tar", "gz")).collect { file ->
                files.add(file)
            }
            stateMutex.withLock {
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
    }

    private fun scanAppDownloads() {
        viewModelScope.launch {
            stateMutex.withLock {
                _uiState.value = _uiState.value.copy(
                    appDownloadStats = _uiState.value.appDownloadStats.copy(isLoading = true)
                )
            }
            val files = mutableListOf<ScannedFile>()
            repository.scanFilesByExtension(listOf("apk", "obb")).collect { file ->
                if (file.size > 1 * 1024 * 1024) files.add(file)
            }
            stateMutex.withLock {
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
    }

    private fun scanOldDownloads() {
        viewModelScope.launch {
            stateMutex.withLock {
                _uiState.value = _uiState.value.copy(
                    oldDownloadStats = _uiState.value.oldDownloadStats.copy(isLoading = true)
                )
            }
            val files = mutableListOf<ScannedFile>()
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) {
                val threeMonthsAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
                repository.scanOldFiles(downloadsDir.absolutePath, threeMonthsAgo).collect { file ->
                    files.add(file)
                }
            }
            stateMutex.withLock {
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
    }

    /** Called after the system delete dialog confirms deletion; removes URIs from all category lists. */
    fun onFilesDeleted(uris: Set<Uri>) {
        _uiState.value = _uiState.value.let { s ->
            s.copy(
                videoStats       = s.videoStats.removeUris(uris),
                archiveStats     = s.archiveStats.removeUris(uris),
                appDownloadStats = s.appDownloadStats.removeUris(uris),
                oldDownloadStats = s.oldDownloadStats.removeUris(uris)
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

private fun CleanupCategoryStats.removeUris(uris: Set<Uri>): CleanupCategoryStats {
    val remaining = files.filterNot { it.uri in uris }
    return copy(files = remaining, totalSize = remaining.sumOf { it.size }, count = remaining.size)
}
