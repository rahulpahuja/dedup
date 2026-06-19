package com.rp.dedup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.repository.ImageScannerRepository
import com.rp.dedup.core.repository.ScannedImageRepository
import com.rp.dedup.core.viewmodels.ScannerViewModel
import com.rp.dedup.core.repository.ScanHistoryRepository

class ScannerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
            val db = AppDatabase.getDatabase(context)
            @Suppress("UNCHECKED_CAST")
            val appContext = context.applicationContext
            return ScannerViewModel(
                context = appContext,
                repository = ImageScannerRepository(appContext),
                historyRepository = ScanHistoryRepository(db.scanHistoryDao()),
                scannedImageRepository = ScannedImageRepository(db.scannedImageDao()),
                dataStoreManager = com.rp.dedup.core.caching.DataStoreManager(appContext),
                analyticsManager = com.rp.dedup.core.analytics.AnalyticsManager(appContext)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
