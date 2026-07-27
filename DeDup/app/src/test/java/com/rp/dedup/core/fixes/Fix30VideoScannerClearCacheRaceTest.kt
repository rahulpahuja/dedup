package com.rp.dedup.core.fixes

import com.rp.dedup.core.viewmodels.VideoScannerViewModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #30 — VideoScannerViewModel.clearCache() cleared in-memory StateFlow values
 * synchronously BEFORE the DB clearAll() coroutine completed:
 *
 *   fun clearCache() {
 *       viewModelScope.launch(Dispatchers.IO) { videoRepository?.clearAll() }  // async
 *       _videos.value = emptyList()   // runs immediately, before DB is cleared
 *       ...
 *   }
 *
 * If the app was killed in the window between the UI reset and the DB write, the old
 * data reloaded on next launch while the UI showed an empty state, causing a mismatch.
 *
 * The fix: all StateFlow mutations are moved inside the IO coroutine, after clearAll()
 * completes, wrapped in withContext(Dispatchers.Main.immediate).
 */
class Fix30VideoScannerClearCacheRaceTest {

    @Test
    fun `VideoScannerViewModel exposes clearCache method`() {
        val method = VideoScannerViewModel::class.java.methods.find { it.name == "clearCache" }
        assertNotNull("clearCache() must be defined on VideoScannerViewModel", method)
    }

    @Test
    fun `clearCache is not a suspend function — it returns Unit synchronously and delegates to a coroutine`() {
        val method = VideoScannerViewModel::class.java.methods.find { it.name == "clearCache" }
        assertNotNull(method)
        // clearCache() launches a coroutine internally; it must not itself be suspend
        // (callers are UI event handlers that cannot be suspend).
        val params = method!!.parameterTypes
        val lastParam = params.lastOrNull()?.name ?: ""
        assertFalse(
            "clearCache must NOT be a suspend function — UI callers expect a plain fun",
            lastParam.contains("Continuation")
        )
        assertEquals(
            "clearCache must return Unit/void",
            Void.TYPE, method.returnType
        )
    }

    @Test
    fun `VideoScannerViewModel has the expected StateFlow fields`() {
        val fieldNames = VideoScannerViewModel::class.java.declaredFields.map { it.name }
        assertTrue("_videos field must exist", fieldNames.contains("_videos"))
        assertTrue("_duplicateGroups field must exist", fieldNames.contains("_duplicateGroups"))
        assertTrue("_scannedCount field must exist", fieldNames.contains("_scannedCount"))
    }
}
