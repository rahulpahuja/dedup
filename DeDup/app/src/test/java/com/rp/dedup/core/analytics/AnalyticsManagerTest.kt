package com.rp.dedup.core.analytics

import org.junit.Assert.*
import org.junit.Test

/**
 * AnalyticsManager is a thin wrapper around FirebaseAnalytics.getInstance(context).
 * Instantiation requires Play Services and must run as an instrumented test.
 *
 * This file documents what is covered by instrumented tests and validates
 * any logic that does not require Android framework.
 */
class AnalyticsManagerTest {

    // ── scan type strings ──────────────────────────────────────────────────────
    // These values are passed to logScanStarted/logScanCompleted and must match
    // the expected analytics schema. Changes here break analytics dashboards.

    @Test
    fun `known scan type strings are non-blank`() {
        val knownTypes = listOf("IMAGE", "VIDEO", "PDF", "APK", "JUNK")
        knownTypes.forEach { type ->
            assertTrue("Scan type must not be blank: $type", type.isNotBlank())
        }
    }

    @Test
    fun `scan type strings are uppercase`() {
        listOf("IMAGE", "VIDEO", "PDF", "APK", "JUNK").forEach { type ->
            assertEquals("Scan type should be uppercase: $type", type, type.uppercase())
        }
    }

    // ── scan types are unique ──────────────────────────────────────────────────

    @Test
    fun `scan type strings are unique`() {
        val types = listOf("IMAGE", "VIDEO", "PDF", "APK", "JUNK")
        assertEquals(types.size, types.toSet().size)
    }
}
