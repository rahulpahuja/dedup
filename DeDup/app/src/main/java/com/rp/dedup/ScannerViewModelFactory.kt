package com.rp.dedup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.ImageScannerRepository
import com.rp.dedup.core.viewmodels.ScannerViewModel
import com.rp.dedup.core.repository.ScanHistoryRepository

class ScannerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScannerViewModel(
                context = context.applicationContext,
                repository = ImageScannerRepository(context),
                historyRepository = ScanHistoryRepository(AppDatabase.getDatabase(context).scanHistoryDao()),
                dataStoreManager = com.rp.dedup.core.caching.DataStoreManager(context),
                analyticsManager = com.rp.dedup.core.analytics.AnalyticsManager(context)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
