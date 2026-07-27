package com.rp.dedup.core.model.state

sealed class CleaningProgress {
    data class Scanning(val filesFound: Int) : CleaningProgress()
    data class Cleaning(val progress: Float, val bytesCleared: Long) : CleaningProgress()
    data class Finished(val totalBytesCleared: Long) : CleaningProgress()
    data class Error(val message: String) : CleaningProgress()
}