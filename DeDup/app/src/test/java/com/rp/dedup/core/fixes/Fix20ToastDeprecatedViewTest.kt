package com.rp.dedup.core.fixes

import com.rp.dedup.core.notifications.ToastManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #20 — ToastManager.showCustom() used Toast.view (deprecated API 30) and
 * Toast.setGravity() (deprecated API 30). On API 30+ the custom view is silently
 * dropped; the message never appeared on ~80%+ of active Android devices.
 *
 * The fix: showCustom() adds a runtime Build.VERSION.SDK_INT >= R check. On API 30+
 * it falls back to Toast.makeText() so the message is always shown. The deprecated
 * path is kept only for API <30 under @Suppress("DEPRECATION").
 */
class Fix20ToastDeprecatedViewTest {

    @Test
    fun `ToastManager exposes showCustom method`() {
        val method = ToastManager::class.java.methods.find { it.name == "showCustom" }
        assertNotNull("showCustom() must be defined on ToastManager", method)
    }

    @Test
    fun `showCustom accepts message as first parameter`() {
        val method = ToastManager::class.java.methods.find { it.name == "showCustom" }
        assertNotNull(method)
        assertEquals(
            "First parameter of showCustom must be String (the message)",
            String::class.java,
            method!!.parameterTypes.firstOrNull()
        )
    }

    @Test
    fun `ToastManager also exposes showShort and showLong for plain toasts`() {
        val showShort = ToastManager::class.java.methods.find {
            it.name == "showShort" && it.parameterTypes.firstOrNull() == String::class.java
        }
        val showLong = ToastManager::class.java.methods.find {
            it.name == "showLong" && it.parameterTypes.firstOrNull() == String::class.java
        }
        assertNotNull("showShort(String) must exist as the API-30+ fallback path", showShort)
        assertNotNull("showLong(String) must exist", showLong)
    }
}
