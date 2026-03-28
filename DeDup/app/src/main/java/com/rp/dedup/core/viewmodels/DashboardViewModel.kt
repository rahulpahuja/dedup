package com.rp.dedup.core.viewmodels

import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.repository.ScanHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StorageStats(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L
) {
    val usedFraction: Float
        get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

class DashboardViewModel(private val historyRepository: ScanHistoryRepository) : ViewModel() {

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    /** Sum of reclaimableBytes across all scan history records. */
    val totalReclaimableBytes: StateFlow<Long> = historyRepository.getAll()
        .map { history -> history.sumOf { it.reclaimableBytes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        loadStorageStats()
    }

    fun refresh() = loadStorageStats()

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
}
