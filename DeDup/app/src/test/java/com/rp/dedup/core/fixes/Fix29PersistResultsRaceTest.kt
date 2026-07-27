package com.rp.dedup.core.fixes

import com.rp.dedup.core.viewmodels.ScannerViewModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #29 — ScannerViewModel.persistResults() was a fire-and-forget
 * `viewModelScope.launch(Dispatchers.IO) { ... }` function. If a new scan started
 * before the previous persist completed:
 *   1. The new scan called clearAll() and reset state.
 *   2. The old fire-and-forget then called clearAll() again + insertImages(oldResults),
 *      overwriting the new scan's in-progress data.
 * Additionally, if the DB write threw, _isStale was never reset to false, causing
 * spurious "data is stale" prompts on every subsequent open.
 *
 * The fix: persistResults() is now a `private suspend fun` called under
 * `withContext(NonCancellable + Dispatchers.IO)` inside the scan coroutine's try block,
 * so it always completes before _isScanning flips back to false. Errors are caught
 * explicitly so _isStale is not left in an incorrect state.
 */
class Fix29PersistResultsRaceTest {

    @Test
    fun `persistResults is a private suspend function not a fire-and-forget launch`() {
        val method = ScannerViewModel::class.java
            .declaredMethods.find { it.name == "persistResults" }
        assertNotNull("persistResults() must still be declared on ScannerViewModel", method)
        assertTrue("persistResults must be private", java.lang.reflect.Modifier.isPrivate(method!!.modifiers))

        // Suspend functions have a Continuation as their last parameter in JVM bytecode
        val params = method.parameterTypes
        val lastParam = params.lastOrNull()?.name ?: ""
        assertTrue(
            "persistResults must be a suspend function (last param is Continuation). " +
                "If it's not suspend it could be launched as fire-and-forget again.",
            lastParam.contains("Continuation")
        )
    }

    @Test
    fun `ScannerViewModel does not expose a public persistResults method`() {
        val publicMethod = ScannerViewModel::class.java.methods.find { it.name == "persistResults" }
        assertNull(
            "persistResults must be private — public exposure would allow external callers to trigger races",
            publicMethod
        )
    }
}
