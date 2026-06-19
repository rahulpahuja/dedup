package com.rp.dedup.core.notifications

import android.content.Context
import android.view.Gravity
import android.widget.Toast
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = com.rp.dedup.util.TestApp::class)
class ToastManagerTest {

    private lateinit var context: Context
    private lateinit var manager: ToastManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = ToastManager(context)
    }

    // ── showShort (String) ─────────────────────────────────────────────────────

    @Test
    fun `showShort with message does not throw`() {
        manager.showShort("hello")
    }

    @Test
    fun `showShort with different messages does not throw`() {
        manager.showShort("message one")
        manager.showShort("message two")
    }

    // ── showLong (String) ──────────────────────────────────────────────────────

    @Test
    fun `showLong with message does not throw`() {
        manager.showLong("hello long")
    }

    // ── showShort (resId) ──────────────────────────────────────────────────────

    @Test
    fun `showShort with valid string resource does not throw`() {
        manager.showShort(android.R.string.ok)
    }

    // ── showLong (resId) ───────────────────────────────────────────────────────

    @Test
    fun `showLong with valid string resource does not throw`() {
        manager.showLong(android.R.string.cancel)
    }

    // ── showCustom — API 30+ fallback ──────────────────────────────────────────

    @Test
    @Config(sdk = [30])
    fun `showCustom on API 30 falls back to plain toast without throwing`() {
        manager.showCustom("fallback message")
    }

    @Test
    @Config(sdk = [31])
    fun `showCustom on API 31 falls back to plain toast without throwing`() {
        manager.showCustom("api 31 message", backgroundColor = "#FF0000", textColor = "#FFFFFF")
    }

    // ── showCustom — API 29 custom view path ───────────────────────────────────

    @Test
    @Config(sdk = [29])
    fun `showCustom on API 29 does not throw without icon`() {
        manager.showCustom(
            message = "custom toast",
            iconRes = null,
            backgroundColor = "#323232",
            textColor = "#FFFFFF",
            cornerRadius = 16f,
            gravity = Gravity.BOTTOM,
            yOffset = 100,
            toastDuration = Toast.LENGTH_SHORT
        )
    }

    @Test
    @Config(sdk = [29])
    fun `showCustom on API 29 with CENTER gravity does not throw`() {
        manager.showCustom(
            message = "centered",
            gravity = Gravity.CENTER,
            yOffset = 0
        )
    }

    @Test
    @Config(sdk = [29])
    fun `showCustom on API 29 with long duration does not throw`() {
        manager.showCustom(message = "long", toastDuration = Toast.LENGTH_LONG)
    }

    // ── constructor ────────────────────────────────────────────────────────────

    @Test
    fun `ToastManager can be constructed with applicationContext`() {
        val appContext = context.applicationContext
        val tm = ToastManager(appContext)
        assertNotNull(tm)
    }

    @Test
    fun `ToastManager accepts activity context without throwing`() {
        val tm = ToastManager(context)
        assertNotNull(tm)
    }
}
