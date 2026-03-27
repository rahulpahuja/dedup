package com.rp.dedup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rp.dedup.core.image.ImageScannerRepository
import com.rp.dedup.core.image.ScannerViewModel

class ScannerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Here we tell it exactly how to build the repository and the ViewModel!
            return ScannerViewModel(ImageScannerRepository(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}