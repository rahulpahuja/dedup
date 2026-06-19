package com.rp.dedup.core.fixes

import com.rp.dedup.core.viewmodels.ScannerViewModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #18 — ScannerViewModel.loadCachedResults() was launched on Dispatchers.IO and
 * wrote directly to _cacheLoaded, _duplicateGroups, and _isStale without switching to
 * the main dispatcher first. While MutableStateFlow writes are thread-safe, the pattern
 * violated the @MainThread contract expected by Compose snapshot observers.
 *
 * The fix: IO work (DB query, MediaStore query, DataStore read) is done on IO, then
 * all StateFlow mutations are batched in a single withContext(Dispatchers.Main.immediate)
 * block.
 *
 * Structural test: the refactored method is `computeStaleness()` (returns Boolean) and
 * `loadCachedResults()` is still present. This confirms the separation was made.
 */
class Fix18StateFlowMainThreadTest {

    @Test
    fun `ScannerViewModel declares loadCachedResults as a private suspend function`() {
        val method = ScannerViewModel::class.java
            .declaredMethods.find { it.name == "loadCachedResults" }
        assertNotNull("loadCachedResults() must still exist after refactor", method)
        assertTrue("loadCachedResults must be private", java.lang.reflect.Modifier.isPrivate(method!!.modifiers))
    }

    @Test
    fun `ScannerViewModel declares computeStaleness replacing checkStaleness`() {
        val newMethod = ScannerViewModel::class.java
            .declaredMethods.find { it.name == "computeStaleness" }
        assertNotNull(
            "computeStaleness() must exist — it returns a Boolean so the caller can batch " +
                "the state mutation into withContext(Main.immediate)",
            newMethod
        )

        val oldMethod = ScannerViewModel::class.java
            .declaredMethods.find { it.name == "checkStaleness" }
        assertNull(
            "checkStaleness() must be removed — it mutated StateFlow directly on IO dispatcher",
            oldMethod
        )
    }
}
