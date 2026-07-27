package com.rp.dedup.core.fixes

import com.rp.dedup.core.image.BestShotAnalyzer
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #9 — BestShotAnalyzer's FaceDetector (lazy singleton) was never closed, leaking
 * the ML Kit native thread pool and model memory (~50-150 MB) for the process lifetime.
 *
 * The fix adds a close() method on BestShotAnalyzer that releases the detector,
 * wired to ScannerViewModel.onCleared().
 */
class Fix9BestShotAnalyzerTest {

    @Test
    fun `BestShotAnalyzer exposes a close() method`() {
        val method = BestShotAnalyzer::class.java.methods.find { it.name == "close" }
        assertNotNull("close() must be defined so callers can release native resources", method)
    }

    @Test
    fun `close() is callable without crashing when detector was never initialised`() {
        // If the lazy faceDetector was never touched (no analyzeGroups call), close()
        // must be a safe no-op rather than throwing NullPointerException.
        assertDoesNotThrow { BestShotAnalyzer.close() }
    }

    @Test
    fun `close() is idempotent — calling twice does not throw`() {
        assertDoesNotThrow {
            BestShotAnalyzer.close()
            BestShotAnalyzer.close()
        }
    }

    @Test
    fun `ScannerViewModel overrides onCleared`() {
        val method = com.rp.dedup.core.viewmodels.ScannerViewModel::class.java
            .declaredMethods.find { it.name == "onCleared" }
        assertNotNull("ScannerViewModel.onCleared() must be overridden to close BestShotAnalyzer", method)
    }

    private fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("Expected no exception but got: $e")
        }
    }
}
