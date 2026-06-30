package com.rp.dedup.core.repository

import com.rp.dedup.core.model.ScannedFile
import kotlinx.coroutines.flow.Flow

interface IFileScannerRepository {
    fun scanFilesByExtension(
        extensions: List<String>,
        deepScan: Boolean = false,
        excludedFolders: List<String> = emptyList()
    ): Flow<ScannedFile>

    fun scanOldFiles(folder: String, olderThanMs: Long): Flow<ScannedFile>
}
