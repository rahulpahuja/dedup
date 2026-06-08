package com.rp.dedup.core.security

import android.content.Context
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RootDetectionManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val packageManager = mockk<PackageManager>(relaxed = true)

    @Before
    fun setUp() {
        every { context.packageManager } returns packageManager
        // By default, all packages throw NameNotFoundException (not installed)
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } throws
            android.content.pm.PackageManager.NameNotFoundException()
    }

    // ── result structure ───────────────────────────────────────────────────────

    @Test
    fun `check returns RootCheckResult with isRooted and triggeredChecks`() {
        val result = RootDetectionManager.check(context)
        assertNotNull(result)
        assertNotNull(result.triggeredChecks)
    }

    @Test
    fun `triggeredChecks is a list`() {
        val result = RootDetectionManager.check(context)
        assertNotNull(result.triggeredChecks)
        assertTrue(result.triggeredChecks is List<*>)
    }

    // ── clean environment ──────────────────────────────────────────────────────

    @Test
    fun `on clean test environment isRooted reflects only actual signals`() {
        val result = RootDetectionManager.check(context)
        // We cannot guarantee the host machine is clean, but we can verify
        // that isRooted == triggeredChecks.isNotEmpty() is always consistent
        assertEquals(result.isRooted, result.triggeredChecks.isNotEmpty())
    }

    @Test
    fun `isRooted is true when any check fires`() {
        // All checks are on real file system paths, but consistency holds
        val result = RootDetectionManager.check(context)
        if (result.triggeredChecks.isNotEmpty()) {
            assertTrue(result.isRooted)
        }
    }

    // ── root package detection ─────────────────────────────────────────────────

    @Test
    fun `when root package is installed checkRootPackages contributes triggered check`() {
        every {
            packageManager.getPackageInfo("com.topjohnwu.magisk", PackageManager.GET_ACTIVITIES)
        } returns mockk()

        val result = RootDetectionManager.check(context)

        assertTrue(result.isRooted)
        assertTrue(result.triggeredChecks.any { it.contains("Root management app") })
    }

    @Test
    fun `when no root packages are installed no package-related trigger fires`() {
        // All packages throw NameNotFoundException from setUp
        val result = RootDetectionManager.check(context)
        // Package check specifically should not have fired
        assertFalse(result.triggeredChecks.any { it.contains("Root management app") })
    }

    @Test
    fun `second root package triggers check as well`() {
        every {
            packageManager.getPackageInfo("eu.chainfire.supersu", PackageManager.GET_ACTIVITIES)
        } returns mockk()

        val result = RootDetectionManager.check(context)

        assertTrue(result.isRooted)
        assertTrue(result.triggeredChecks.any { it.contains("Root management app") })
    }

    // ── multiple triggers ──────────────────────────────────────────────────────

    @Test
    fun `multiple packages installed still produces single triggered check entry`() {
        every {
            packageManager.getPackageInfo("com.topjohnwu.magisk", PackageManager.GET_ACTIVITIES)
        } returns mockk()
        every {
            packageManager.getPackageInfo("eu.chainfire.supersu", PackageManager.GET_ACTIVITIES)
        } returns mockk()

        val result = RootDetectionManager.check(context)

        // Root packages contribute a single aggregated trigger, not one per package
        assertEquals(1, result.triggeredChecks.count { it.contains("Root management app") })
    }

    // ── exception safety ───────────────────────────────────────────────────────

    @Test
    fun `check never throws even when packageManager throws unexpected exception`() {
        every {
            packageManager.getPackageInfo(any<String>(), any<Int>())
        } throws RuntimeException("Unexpected PM failure")

        // Should not throw; exceptions are swallowed per-check
        val result = RootDetectionManager.check(context)
        assertNotNull(result)
    }
}
