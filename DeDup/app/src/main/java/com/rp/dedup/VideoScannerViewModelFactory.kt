package com.rp.dedup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rp.dedup.core.VideoScannerRepository
import com.rp.dedup.core.VideoScannerViewModel
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.scanhistory.ScanHistoryRepository

class VideoScannerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoScannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoScannerViewModel(
                repository = VideoScannerRepository(context),
                historyRepository = ScanHistoryRepository(AppDatabase.getDatabase(context).scanHistoryDao())
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
