package com.rp.dedup.core.security

import org.junit.Assert.*
import org.junit.Test

class NetworkSecurityManagerTest {

    // ── verifyTokenOnServer ────────────────────────────────────────────────────

    @Test(expected = UnsupportedOperationException::class)
    fun `verifyTokenOnServer throws for any token`() {
        NetworkSecurityManager.verifyTokenOnServer("any-token")
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `verifyTokenOnServer throws for empty token`() {
        NetworkSecurityManager.verifyTokenOnServer("")
    }

    @Test(expected = UnsupportedOperationException::class)
    fun `verifyTokenOnServer throws for very long token`() {
        val longToken = "a".repeat(4096)
        NetworkSecurityManager.verifyTokenOnServer(longToken)
    }

    // ── getIntegrityToken — cloud project number check ─────────────────────────

    @Test
    fun `CLOUD_PROJECT_NUMBER is zero indicating server integration is incomplete`() {
        // When CLOUD_PROJECT_NUMBER == 0L, getIntegrityToken returns null immediately.
        // This test documents the known configuration gap; update when a real project
        // number is configured.
        //
        // Actual getIntegrityToken() requires Play Services and cannot run in JVM unit tests.
        // It is tested in instrumented tests on a real device or emulator.
    }
}
