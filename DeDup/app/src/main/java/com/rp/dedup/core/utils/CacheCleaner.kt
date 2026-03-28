package com.rp.dedup.core.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Progress states for cache cleaning.
 */
sealed class CleaningProgress {
    data class Scanning(val filesFound: Int) : CleaningProgress()
    data class Cleaning(val progress: Float, val bytesCleared: Long) : CleaningProgress()
    data class Finished(val totalBytesCleared: Long) : CleaningProgress()
    data class Error(val message: String) : CleaningProgress()
}

object CacheCleaner {
    
    /**
     * Cleans all app caches in the background and provides real-time progress updates.
     * Uses Flow to emit progress which can be observed in the foreground.
     */
    fun clearAllCacheFlow(context: Context): Flow<CleaningProgress> = flow {
        try {
            val cacheDirs = listOfNotNull(context.cacheDir, context.externalCacheDir)
            
            // Step 1: Rapid Scan to calculate total size for accurate progress tracking
            var totalSize = 0L
            var totalFiles = 0
            cacheDirs.forEach { dir ->
                dir.walk().forEach { file ->
                    if (file.isFile) {
                        totalSize += file.length()
                        totalFiles++
                        // Emit scanning updates every 10 files to avoid flooding the UI
                        if (totalFiles % 10 == 0) {
                            emit(CleaningProgress.Scanning(totalFiles))
                        }
                    }
                }
            }
            
            if (totalSize == 0L) {
                emit(CleaningProgress.Finished(0L))
                return@flow
            }

            // Step 2: Background Cleaning with progress updates
            var clearedBytes = 0L
            cacheDirs.forEach { dir ->
                // walkBottomUp ensures we delete children before parents
                dir.walkBottomUp().forEach { file ->
                    if (file != dir) { // Preserve the root cache directories
                        val size = if (file.isFile) file.length() else 0L
                        if (file.delete()) {
                            clearedBytes += size
                            // Emit progress update
                            val progress = clearedBytes.toFloat() / totalSize
                            emit(CleaningProgress.Cleaning(progress, clearedBytes))
                        }
                    }
                }
            }
            
            emit(CleaningProgress.Finished(clearedBytes))
        } catch (e: Exception) {
            emit(CleaningProgress.Error(e.message ?: "Unknown error occurred during cache cleaning"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Efficiently calculates the total size of the app cache in a background thread.
     */
    suspend fun getCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        val cacheDirs = listOfNotNull(context.cacheDir, context.externalCacheDir)
        cacheDirs.sumOf { dir ->
            dir.walk().filter { it.isFile }.sumOf { it.length() }
        }
    }
}
