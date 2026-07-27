package com.rp.dedup.core.fixes

import com.rp.dedup.core.security.NetworkSecurityManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #19 — NetworkSecurityManager.verifyTokenOnServer() unconditionally returned true,
 * making Play Integrity token attestation a no-op. Any caller gating a security decision
 * on this method would always be bypassed.
 *
 * The fix: the method now throws UnsupportedOperationException so callers fail
 * explicitly rather than silently trusting unverified tokens.
 */
class Fix19NetworkSecurityVerifyTokenTest {

    @Test
    fun `verifyTokenOnServer throws UnsupportedOperationException`() {
        try {
            NetworkSecurityManager.verifyTokenOnServer("fake-token")
            fail("verifyTokenOnServer must throw UnsupportedOperationException — it must not silently return true")
        } catch (e: UnsupportedOperationException) {
            // expected
        }
    }

    @Test
    fun `verifyTokenOnServer does not return true`() {
        var returned: Boolean? = null
        try {
            returned = NetworkSecurityManager.verifyTokenOnServer("fake-token")
        } catch (_: UnsupportedOperationException) {
            // The throw is the correct behaviour — test passes
            return
        }
        assertNotEquals(
            "verifyTokenOnServer must not silently return true for unverified tokens",
            true, returned
        )
    }
}
