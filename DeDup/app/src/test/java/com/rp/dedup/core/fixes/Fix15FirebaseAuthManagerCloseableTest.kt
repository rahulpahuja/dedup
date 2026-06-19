package com.rp.dedup.core.fixes

import com.rp.dedup.core.firebase.auth.FirebaseAuthManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #15 — FirebaseAuthManager owned a CoroutineScope(Dispatchers.Main + SupervisorJob())
 * that was never cancelled. Any remember { FirebaseAuthManager(...) } site accumulated
 * zombie scopes that kept running indefinitely.
 *
 * The fix: FirebaseAuthManager implements Closeable; close() cancels the SupervisorJob.
 * Call sites (SplashScreen, LoginScreen) add DisposableEffect { onDispose { close() } }.
 */
class Fix15FirebaseAuthManagerCloseableTest {

    @Test
    fun `FirebaseAuthManager implements Closeable`() {
        assertTrue(
            "FirebaseAuthManager must implement java.io.Closeable so the CoroutineScope can be cancelled",
            java.io.Closeable::class.java.isAssignableFrom(FirebaseAuthManager::class.java)
        )
    }

    @Test
    fun `FirebaseAuthManager exposes a close() method`() {
        val method = FirebaseAuthManager::class.java.methods.find { it.name == "close" }
        assertNotNull("close() method must be declared", method)
        assertEquals("close() must return void", Void.TYPE, method!!.returnType)
    }

    @Test
    fun `FirebaseAuthManager has a SupervisorJob field that close() can cancel`() {
        val jobField = FirebaseAuthManager::class.java.declaredFields.find { it.name == "job" }
        assertNotNull(
            "FirebaseAuthManager must hold a 'job' field (SupervisorJob) that close() cancels",
            jobField
        )
    }
}
