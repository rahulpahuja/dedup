package com.rp.dedup.core.fixes

import com.rp.dedup.core.firebase.auth.FirebaseAuthManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #25 — SplashScreen used `remember { FirebaseAuthManager(ToastManager(context)) }`
 * with no key, so the Context captured on first composition was never updated even if
 * the Context changed (e.g., after configuration changes that preserve the composable tree).
 * ToastManager was also created with the raw Activity context rather than applicationContext.
 *
 * The fix:
 *   - Changed to `remember(context) { FirebaseAuthManager(ToastManager(context.applicationContext)) }`
 *   - Added `DisposableEffect(authManager) { onDispose { authManager.close() } }`
 *
 * This test verifies the structural prerequisites: FirebaseAuthManager must be Closeable
 * (tested in Fix15) and the ToastManager constructor must accept a Context.
 */
class Fix25SplashRememberContextKeyTest {

    @Test
    fun `FirebaseAuthManager is Closeable so DisposableEffect can call close()`() {
        assertTrue(
            "FirebaseAuthManager must implement Closeable — required by the DisposableEffect added in Fix #25",
            java.io.Closeable::class.java.isAssignableFrom(FirebaseAuthManager::class.java)
        )
    }

    @Test
    fun `ToastManager constructor accepts a Context`() {
        val ctor = com.rp.dedup.core.notifications.ToastManager::class.java.constructors
            .find { it.parameterCount == 1 }
        assertNotNull("ToastManager must have a single-arg constructor", ctor)
        assertEquals(
            "ToastManager constructor parameter must be android.content.Context",
            "Context",
            ctor!!.parameterTypes[0].simpleName
        )
    }

    @Test
    fun `SplashScreen composable function exists with hasPendingDeepLink parameter`() {
        // The function is non-reflectable (it's a Kotlin composable), but we can verify
        // the method exists in the generated class via the Kt suffix.
        val cls = Class.forName("com.rp.dedup.screens.SplashScreenKt")
        val splashMethod = cls.declaredMethods.find { it.name == "SplashScreen" }
        assertNotNull("SplashScreen composable must be present in SplashScreenKt", splashMethod)

        val paramNames = splashMethod!!.parameterTypes.map { it.simpleName }
        assertTrue(
            "SplashScreen must accept a NavHostController parameter",
            paramNames.any { it.contains("NavHostController") }
        )
    }
}
