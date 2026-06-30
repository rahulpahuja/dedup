package com.rp.dedup.core.viewmodels

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.model.MediaCounts
import com.rp.dedup.core.model.StorageStats
import com.rp.dedup.core.repository.ScanHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val historyRepository: ScanHistoryRepository,
    context: Context
) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            return DashboardViewModel(
                ScanHistoryRepository(AppDatabase.getDatabase(appContext).scanHistoryDao()),
                appContext
            ) as T
        }
    }

    private val context: Context = context.applicationContext

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _mediaCounts = MutableStateFlow(MediaCounts())
    val mediaCounts: StateFlow<MediaCounts> = _mediaCounts.asStateFlow()

    /** Sum of reclaimableBytes across all scan history records. */
    val totalReclaimableBytes: StateFlow<Long> = historyRepository.getAll()
        .map { history -> history.sumOf { it.reclaimableBytes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        loadStorageStats()
        loadMediaCounts()
    }

    fun refresh() {
        loadStorageStats()
        loadMediaCounts()
    }

    private fun loadStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                val blockSize = stat.blockSizeLong
                val total = stat.blockCountLong * blockSize
                val free = stat.availableBlocksLong * blockSize
                _storageStats.value = StorageStats(
                    totalBytes = total,
                    usedBytes = total - free,
                    freeBytes = free
                )
            } catch (_: Exception) { /* leave defaults */ }
        }
    }

    private fun loadMediaCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val cr = context.contentResolver
            val images = cr.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID), null, null, null
            )?.use { it.count } ?: 0

            val videos = cr.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID), null, null, null
            )?.use { it.count } ?: 0

            val filesUri = MediaStore.Files.getContentUri("external")
            val pdfs = cr.query(
                filesUri,
                arrayOf(MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ?",
                arrayOf("application/pdf"), null
            )?.use { it.count } ?: 0

            val apks = cr.query(
                filesUri,
                arrayOf(MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ?",
                arrayOf("application/vnd.android.package-archive"), null
            )?.use { it.count } ?: 0

            _mediaCounts.value = MediaCounts(images = images, videos = videos, pdfs = pdfs, apks = apks)
        }
    }
}
