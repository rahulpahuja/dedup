package com.rp.dedup.core.permissions

import android.Manifest
import android.app.Application
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
class PermissionManagerTest {

    private lateinit var context: Context
    private lateinit var manager: PermissionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = PermissionManager(context)
    }

    // ── companion lists non-empty ──────────────────────────────────────────────

    @Test
    fun `IMAGE permission list is non-empty`() {
        assertTrue(PermissionManager.IMAGE.isNotEmpty())
    }

    @Test
    fun `VIDEO permission list is non-empty`() {
        assertTrue(PermissionManager.VIDEO.isNotEmpty())
    }

    @Test
    fun `AUDIO permission list is non-empty`() {
        assertTrue(PermissionManager.AUDIO.isNotEmpty())
    }

    @Test
    fun `CONTACTS list contains READ and WRITE contacts`() {
        assertTrue(PermissionManager.CONTACTS.contains(Manifest.permission.READ_CONTACTS))
        assertTrue(PermissionManager.CONTACTS.contains(Manifest.permission.WRITE_CONTACTS))
    }

    @Test
    fun `SMS list contains READ_SMS and RECEIVE_SMS`() {
        assertTrue(PermissionManager.SMS.contains(Manifest.permission.READ_SMS))
        assertTrue(PermissionManager.SMS.contains(Manifest.permission.RECEIVE_SMS))
    }

    @Test
    fun `ALL_MEDIA contains no duplicate permissions`() {
        val list = PermissionManager.ALL_MEDIA
        assertEquals("ALL_MEDIA must not contain duplicates", list.size, list.distinct().size)
    }

    @Test
    fun `ALL contains no duplicate permissions`() {
        val list = PermissionManager.ALL
        assertEquals("ALL must not contain duplicates", list.size, list.distinct().size)
    }

    @Test
    fun `ALL_MEDIA is a subset of ALL`() {
        assertTrue(PermissionManager.ALL.containsAll(PermissionManager.ALL_MEDIA))
    }

    // ── isGranted ──────────────────────────────────────────────────────────────

    @Test
    fun `isGranted returns false for denied permission`() {
        assertFalse(manager.isGranted(Manifest.permission.READ_CONTACTS))
    }

    @Test
    fun `isGranted returns true after permission granted`() {
        shadowOf(context.applicationContext as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        assertTrue(manager.isGranted(Manifest.permission.READ_CONTACTS))
    }

    // ── areAllGranted ──────────────────────────────────────────────────────────

    @Test
    fun `areAllGranted returns true for empty list`() {
        assertTrue(manager.areAllGranted(emptyList()))
    }

    @Test
    fun `areAllGranted returns false when any permission denied`() {
        shadowOf(context.applicationContext as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        assertFalse(manager.areAllGranted(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS)))
    }

    @Test
    fun `areAllGranted returns true when all granted`() {
        shadowOf(context.applicationContext as Application).grantPermissions(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        assertTrue(manager.areAllGranted(PermissionManager.CONTACTS))
    }

    // ── filterDenied ───────────────────────────────────────────────────────────

    @Test
    fun `filterDenied returns empty for empty input`() {
        assertTrue(manager.filterDenied(emptyList()).isEmpty())
    }

    @Test
    fun `filterDenied returns all when none granted`() {
        val perms = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        assertEquals(perms, manager.filterDenied(perms))
    }

    @Test
    fun `filterDenied excludes already granted permissions`() {
        shadowOf(context.applicationContext as Application).grantPermissions(Manifest.permission.READ_CONTACTS)
        val denied = manager.filterDenied(PermissionManager.CONTACTS)
        assertFalse(denied.contains(Manifest.permission.READ_CONTACTS))
        assertTrue(denied.contains(Manifest.permission.WRITE_CONTACTS))
    }

    @Test
    fun `filterDenied returns empty when all granted`() {
        shadowOf(context.applicationContext as Application).grantPermissions(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        assertTrue(manager.filterDenied(PermissionManager.CONTACTS).isEmpty())
    }

    // ── convenience properties ────────────────────────────────────────────────

    @Test
    fun `hasContactAccess is false when contacts permission not granted`() {
        assertFalse(manager.hasContactAccess)
    }

    @Test
    fun `hasContactAccess is true when both contact permissions granted`() {
        shadowOf(context.applicationContext as Application).grantPermissions(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        assertTrue(manager.hasContactAccess)
    }

    @Test
    fun `hasNotificationAccess is false on API 33 when permission not granted`() {
        assertFalse(manager.hasNotificationAccess)
    }

    @Test
    @Config(sdk = [32])
    fun `hasNotificationAccess is true on API 32 without runtime grant`() {
        val mgr = PermissionManager(context)
        assertTrue(mgr.hasNotificationAccess)
    }

    @Test
    fun `hasSmsAccess is false when sms permissions not granted`() {
        assertFalse(manager.hasSmsAccess)
    }

    // ── NOTIFICATIONS list ─────────────────────────────────────────────────────

    @Test
    fun `NOTIFICATIONS list is non-empty on API 33`() {
        assertTrue(PermissionManager.NOTIFICATIONS.isNotEmpty())
    }

    @Test
    @Config(sdk = [32])
    fun `NOTIFICATIONS list is empty on API 32`() {
        assertTrue(PermissionManager.NOTIFICATIONS.isEmpty())
    }
}
