package com.rp.dedup.core.fixes

import com.rp.dedup.core.search.SmartJunkRepository
import com.rp.dedup.core.viewmodels.SmartJunkViewModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #16 — SmartJunkRepository created an ML Kit ImageLabeler at construction time
 * but never called labeler.close(). SmartJunkViewModel had no onCleared() override,
 * so the native thread pool and model weights leaked for the process lifetime.
 *
 * The fix: SmartJunkRepository implements Closeable with close() → labeler.close().
 * SmartJunkViewModel.onCleared() calls repository.close().
 */
class Fix16SmartJunkLabelerLeakTest {

    @Test
    fun `SmartJunkRepository implements Closeable`() {
        assertTrue(
            "SmartJunkRepository must implement Closeable to allow callers to release the ML Kit labeler",
            java.io.Closeable::class.java.isAssignableFrom(SmartJunkRepository::class.java)
        )
    }

    @Test
    fun `SmartJunkRepository exposes close() method`() {
        val method = SmartJunkRepository::class.java.methods.find { it.name == "close" }
        assertNotNull("close() must be declared on SmartJunkRepository", method)
    }

    @Test
    fun `SmartJunkViewModel overrides onCleared`() {
        val method = SmartJunkViewModel::class.java
            .declaredMethods.find { it.name == "onCleared" }
        assertNotNull(
            "SmartJunkViewModel must override onCleared() to release the ML Kit labeler",
            method
        )
    }
}
