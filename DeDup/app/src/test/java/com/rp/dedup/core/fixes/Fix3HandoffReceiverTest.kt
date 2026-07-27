package com.rp.dedup.core.fixes

import com.rp.dedup.core.handoff.HandoffReceiver
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #3 — HandoffReceiver was exported=true and responded to an implicit broadcast
 * any third-party app could send. The fix:
 *  1. exported=false in the manifest (only this app can send the intent)
 *  2. Action changed to a namespaced constant so accidental collisions are impossible
 *  3. buildIntent() helper sets the package so the system won't deliver it implicitly
 */
class Fix3HandoffReceiverTest {

    @Test
    fun `ACTION_NAVIGATE is namespaced to this app package`() {
        assertTrue(
            "Action must be scoped to the app package to avoid broadcast hijacking",
            HandoffReceiver.ACTION_NAVIGATE.startsWith("com.rp.dedup.")
        )
    }

    @Test
    fun `ACTION_NAVIGATE does not use the old system action that third-party apps could send`() {
        assertNotEquals(
            "Must not reuse the system-level action",
            "android.intent.action.HANDOFF_RECEIVED",
            HandoffReceiver.ACTION_NAVIGATE
        )
    }

    @Test
    fun `EXTRA_ROUTE constant is defined correctly`() {
        assertEquals("target_route", HandoffReceiver.EXTRA_ROUTE)
    }

    @Test
    fun `buildIntent companion function exists and is callable`() {
        // Verify the buildIntent helper is accessible — the actual Intent content
        // is verified in instrumented tests where the Android SDK is fully available.
        val method = HandoffReceiver.Companion::class.java
            .methods
            .any { it.name == "buildIntent" }
        assertTrue("buildIntent must be defined on the companion", method)
    }

    @Test
    fun `HandoffReceiver class is in the expected package`() {
        assertEquals("com.rp.dedup.core.handoff", HandoffReceiver::class.java.packageName)
    }
}
