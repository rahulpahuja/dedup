package com.rp.dedup.core.utils

import android.content.Context
import android.util.Log
import com.rp.dedup.core.model.CleaningProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

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
                        val deleted = file.delete()
                        if (deleted) {
                            clearedBytes += size
                        } else if (file.exists()) {
                            Log.w("CacheCleaner", "Failed to delete: ${file.absolutePath}")
                        }
                        
                        // Emit progress update even if delete fails (to keep UI moving)
                        val progress = if (totalSize > 0) clearedBytes.toFloat() / totalSize else 1.0f
                        emit(CleaningProgress.Cleaning(progress.coerceAtMost(1.0f), clearedBytes))
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
