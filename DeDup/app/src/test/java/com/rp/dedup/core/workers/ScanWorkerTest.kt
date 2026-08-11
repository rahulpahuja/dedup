package com.rp.dedup.core.workers

import android.net.Uri
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanWorkerTest {

    private fun uri(): Uri = mockk(relaxed = true)

    @Test
    fun `computeReclaimableBytes sums all but the first file per group`() {
        val a1 = uri(); val a2 = uri(); val a3 = uri()
        val b1 = uri(); val b2 = uri()
        val groupA = listOf(a1, a2, a3)
        val groupB = listOf(b1, b2)
        val sizes = mapOf(a1 to 100L, a2 to 50L, a3 to 25L, b1 to 10L, b2 to 5L)

        val reclaimable = ScanWorker.computeReclaimableBytes(listOf(groupA, groupB)) { u -> sizes.getValue(u) }

        // Keeper (first item) in each group is excluded: a2+a3 + b2 = 50+25+5
        assertEquals(80L, reclaimable)
    }

    @Test
    fun `computeReclaimableBytes returns zero for no duplicate groups`() {
        val reclaimable = ScanWorker.computeReclaimableBytes(emptyList()) { 999L }

        assertEquals(0L, reclaimable)
    }

    @Test
    fun `computeReclaimableBytes ignores single-item groups entirely`() {
        val singleton = listOf(uri())

        val reclaimable = ScanWorker.computeReclaimableBytes(listOf(singleton)) { 42L }

        assertEquals(0L, reclaimable)
    }

    @Test
    fun `shouldNotify is false below the 50MB threshold`() {
        assertFalse(ScanWorker.shouldNotify(50L * 1024 * 1024 - 1))
    }

    @Test
    fun `shouldNotify is true at or above the 50MB threshold`() {
        assertTrue(ScanWorker.shouldNotify(50L * 1024 * 1024))
        assertTrue(ScanWorker.shouldNotify(100L * 1024 * 1024))
    }

    @Test
    fun `formatBytes renders human-readable units`() {
        assertEquals("512 B", ScanWorker.formatBytes(512))
        assertEquals("1.0 KB", ScanWorker.formatBytes(1024))
        assertEquals("2.5 MB", ScanWorker.formatBytes((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", ScanWorker.formatBytes(1024L * 1024 * 1024))
    }
}
