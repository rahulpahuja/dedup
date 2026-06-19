package com.rp.dedup.core.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = com.rp.dedup.util.TestApp::class)
class AppNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var manager: AppNotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = AppNotificationManager(context)
    }

    // ── companion constants ────────────────────────────────────────────────────

    @Test
    fun `CHANNEL_URGENT_ID is non-empty`() {
        assertTrue(AppNotificationManager.CHANNEL_URGENT_ID.isNotEmpty())
    }

    @Test
    fun `CHANNEL_DEFAULT_ID is non-empty and distinct from urgent`() {
        assertTrue(AppNotificationManager.CHANNEL_DEFAULT_ID.isNotEmpty())
        assertNotEquals(AppNotificationManager.CHANNEL_URGENT_ID, AppNotificationManager.CHANNEL_DEFAULT_ID)
    }

    @Test
    fun `ACTION_POSITIVE and ACTION_NEGATIVE are distinct`() {
        assertNotEquals(AppNotificationManager.ACTION_POSITIVE, AppNotificationManager.ACTION_NEGATIVE)
    }

    @Test
    fun `EXTRA_NOTIFICATION_ID is non-empty`() {
        assertTrue(AppNotificationManager.EXTRA_NOTIFICATION_ID.isNotEmpty())
    }

    // ── hasNotificationPermission ──────────────────────────────────────────────

    @Test
    @Config(sdk = [32])
    fun `hasNotificationPermission returns true on API 32 without runtime grant`() {
        val mgr = AppNotificationManager(context)
        assertTrue(mgr.hasNotificationPermission())
    }

    @Test
    @Config(sdk = [33])
    fun `hasNotificationPermission returns false on API 33 when POST_NOTIFICATIONS denied`() {
        assertFalse(manager.hasNotificationPermission())
    }

    @Test
    @Config(sdk = [33])
    fun `hasNotificationPermission returns true on API 33 when POST_NOTIFICATIONS granted`() {
        shadowOf(context.applicationContext as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(manager.hasNotificationPermission())
    }

    // ── notification channels created on init ──────────────────────────────────

    @Test
    fun `init creates urgent notification channel`() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(AppNotificationManager.CHANNEL_URGENT_ID)
        assertNotNull("Urgent channel must be created on init", channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel!!.importance)
    }

    @Test
    fun `init creates default notification channel`() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(AppNotificationManager.CHANNEL_DEFAULT_ID)
        assertNotNull("Default channel must be created on init", channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel!!.importance)
    }

    // ── cancelNotification ─────────────────────────────────────────────────────

    @Test
    fun `cancelNotification on non-existent id does not throw`() {
        manager.cancelNotification(9999)
    }

    @Test
    fun `cancelNotification is idempotent`() {
        manager.cancelNotification(42)
        manager.cancelNotification(42)
    }

    // ── showSimpleNotification — no permission ────────────────────────────────

    @Test
    @Config(sdk = [33])
    fun `showSimpleNotification is skipped when permission not granted`() {
        manager.showSimpleNotification(1, "Title", "Body")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(0, shadowOf(nm).size())
    }

    // ── showSimpleNotification — with permission ──────────────────────────────

    @Test
    @Config(sdk = [32])
    fun `showSimpleNotification posts notification on API 32`() {
        val mgr = AppNotificationManager(context)
        mgr.showSimpleNotification(7, "Hello", "World")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(1, shadowOf(nm).size())
    }
}
