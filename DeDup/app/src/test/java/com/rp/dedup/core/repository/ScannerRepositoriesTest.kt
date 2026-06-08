package com.rp.dedup.core.repository

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Structural tests for repositories whose core logic is MediaStore queries.
 * Full scan behaviour requires a real device / ContentProvider and is covered
 * by instrumented tests. These tests verify:
 *  - The repositories can be instantiated without crashing
 *  - Their scanXxx() methods return a non-null Flow
 */
class ScannerRepositoriesTest {

    private val context = mockk<Context>(relaxed = true).also {
        every { it.contentResolver } returns mockk(relaxed = true)
    }

    // ── FileScannerRepository ──────────────────────────────────────────────────

    @Test
    fun `FileScannerRepository can be instantiated`() {
        val repo = FileScannerRepository(context)
        assertNotNull(repo)
    }

    @Test
    fun `FileScannerRepository scanFilesByExtension returns a Flow`() {
        val repo = FileScannerRepository(context)
        val flow = repo.scanFilesByExtension(listOf("pdf"))
        assertNotNull(flow)
    }

    @Test
    fun `FileScannerRepository scanFilesByExtension accepts multiple extensions`() {
        val repo = FileScannerRepository(context)
        val flow = repo.scanFilesByExtension(listOf("pdf", "apk", "zip"))
        assertNotNull(flow)
    }

    @Test
    fun `FileScannerRepository scanFilesByExtension accepts empty list`() {
        val repo = FileScannerRepository(context)
        val flow = repo.scanFilesByExtension(emptyList())
        assertNotNull(flow)
    }

    // ── VideoScannerRepository ─────────────────────────────────────────────────

    @Test
    fun `VideoScannerRepository can be instantiated`() {
        val repo = VideoScannerRepository(context)
        assertNotNull(repo)
    }

    @Test
    fun `VideoScannerRepository scanVideos returns a Flow`() {
        val repo = VideoScannerRepository(context)
        val flow = repo.scanVideos()
        assertNotNull(flow)
    }

    @Test
    fun `VideoScannerRepository scanVideos deepScan parameter is accepted`() {
        val repo = VideoScannerRepository(context)
        val flow = repo.scanVideos(deepScan = true)
        assertNotNull(flow)
    }

    // ── ImageScannerRepository ─────────────────────────────────────────────────

    @Test
    fun `ImageScannerRepository can be instantiated`() {
        val repo = ImageScannerRepository(context)
        assertNotNull(repo)
    }
}
