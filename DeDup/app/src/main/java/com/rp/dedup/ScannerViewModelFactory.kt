package com.rp.dedup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.image.ImageScannerRepository
import com.rp.dedup.core.image.ScannerViewModel
import com.rp.dedup.core.scanhistory.ScanHistoryRepository

class ScannerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScannerViewModel(
                repository = ImageScannerRepository(context),
                historyRepository = ScanHistoryRepository(AppDatabase.getDatabase(context).scanHistoryDao())
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
