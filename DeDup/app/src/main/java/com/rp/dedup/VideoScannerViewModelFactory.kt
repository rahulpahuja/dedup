package com.rp.dedup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rp.dedup.core.VideoScannerRepository
import com.rp.dedup.core.VideoScannerViewModel

class VideoScannerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoScannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoScannerViewModel(VideoScannerRepository(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
