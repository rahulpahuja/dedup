package com.rp.dedup.core.viewmodels

import android.net.Uri
import com.rp.dedup.core.model.ScannedVideo
import com.rp.dedup.core.repository.VideoScannerRepository
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Extended coverage for VideoScannerViewModel, complementing the existing
 * VideoScannerViewModelTest with additional grouping, edge-case, and state tests.
 */
class VideoScannerViewModelExtendedTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<VideoScannerRepository>()
    private lateinit var viewModel: VideoScannerViewModel

    private fun video(id: Int, size: Long = id * 1_000L, duration: Long = id * 1_000L) = ScannedVideo(
        uri = mockk<Uri>(),
        name = "video$id.mp4",
        sizeInBytes = size,
        durationMs = duration,
        mimeType = "video/mp4",
        path = "/storage/emulated/0/video$id.mp4"
    )

    @Before
    fun setUp() {
        // Match the pattern of VideoScannerViewModelTest exactly so init's IO coroutine
        // runs before runTest and doesn't race with test assertions.
        viewModel = VideoScannerViewModel(repository, defaultDispatcher = coroutineRule.testDispatcher, ioDispatcher = coroutineRule.testDispatcher)
    }

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun `scannedCount starts at zero`() {
        assertEquals(0, viewModel.scannedCount.value)
    }

    @Test
    fun `resumedCount starts at zero`() {
        assertEquals(0, viewModel.resumedCount.value)
    }

    // ── duplicate grouping by size ────────────────────────────────────────────

    @Test
    fun `two videos with same size but different duration form one group`() = runTest {
        val v1 = video(1, size = 5_000L, duration = 1_000L)
        val v2 = video(2, size = 5_000L, duration = 2_000L)
        coEvery { repository.scanVideos(any()) } returns flowOf(v1, v2)
        viewModel.startScanning()
        assertEquals(1, viewModel.duplicateGroups.value.size)
        assertEquals(2, viewModel.duplicateGroups.value[0].size)
    }

    @Test
    fun `single video produces no duplicate groups`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flowOf(video(1))
        viewModel.startScanning()
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `three videos with same size form one group of three`() = runTest {
        val v1 = video(1, size = 3_000L)
        val v2 = video(2, size = 3_000L)
        val v3 = video(3, size = 3_000L)
        coEvery { repository.scanVideos(any()) } returns flowOf(v1, v2, v3)
        viewModel.startScanning()
        assertEquals(1, viewModel.duplicateGroups.value.size)
        assertEquals(3, viewModel.duplicateGroups.value[0].size)
    }

    @Test
    fun `videos with all different sizes produce no duplicate groups`() = runTest {
        val v1 = video(1, size = 1_000L)
        val v2 = video(2, size = 2_000L)
        val v3 = video(3, size = 3_000L)
        coEvery { repository.scanVideos(any()) } returns flowOf(v1, v2, v3)
        viewModel.startScanning()
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `two independent size groups produce two separate duplicate groups`() = runTest {
        val a1 = video(1, size = 1_000L)
        val a2 = video(2, size = 1_000L)
        val b1 = video(3, size = 2_000L)
        val b2 = video(4, size = 2_000L)
        coEvery { repository.scanVideos(any()) } returns flowOf(a1, a2, b1, b2)
        viewModel.startScanning()
        assertEquals(2, viewModel.duplicateGroups.value.size)
    }

    @Test
    fun `50 videos with same size form one group of 50`() = runTest {
        val videos = (1..50).map { video(it, size = 9_999L) }
        coEvery { repository.scanVideos(any()) } returns flowOf(*videos.toTypedArray())
        viewModel.startScanning()
        assertEquals(1, viewModel.duplicateGroups.value.size)
        assertEquals(50, viewModel.duplicateGroups.value[0].size)
    }

    // ── forceRescan clears previous results ───────────────────────────────────

    @Test
    fun `forceRescan clears videos list before re-scanning`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flowOf(video(1))
        viewModel.startScanning()
        assertEquals(1, viewModel.videos.value.size)

        coEvery { repository.scanVideos(any()) } returns flowOf()
        viewModel.startScanning(forceRescan = true)
        assertEquals(0, viewModel.videos.value.size)
    }

    @Test
    fun `forceRescan clears duplicate groups before re-scanning`() = runTest {
        val v1 = video(1, size = 500L)
        val v2 = video(2, size = 500L)
        coEvery { repository.scanVideos(any()) } returns flowOf(v1, v2)
        viewModel.startScanning()
        assertEquals(1, viewModel.duplicateGroups.value.size)

        coEvery { repository.scanVideos(any()) } returns flowOf()
        viewModel.startScanning(forceRescan = true)
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    // ── cancelScanning state ──────────────────────────────────────────────────

    @Test
    fun `cancelScanning while idle is safe and leaves isScanning false`() {
        viewModel.cancelScanning()
        assertFalse(viewModel.isScanning.value)
    }

    @Test
    fun `cancelScanning stops an active scan`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        assertTrue(viewModel.isScanning.value)
        viewModel.cancelScanning()
        assertFalse(viewModel.isScanning.value)
    }

    @Test
    fun `after cancel a new scan can be started successfully`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        viewModel.cancelScanning()

        coEvery { repository.scanVideos(any()) } returns flowOf(video(1))
        viewModel.startScanning()
        assertEquals(1, viewModel.videos.value.size)
    }

    // ── idempotent startScanning ──────────────────────────────────────────────

    @Test
    fun `startScanning while already scanning is a no-op`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        viewModel.startScanning()
        coVerify(exactly = 1) { repository.scanVideos(any()) }
    }

    // ── videos list accumulation ──────────────────────────────────────────────

    @Test
    fun `videos list contains all emitted videos after scan completes`() = runTest {
        val videos = (1..15).map { video(it) }
        coEvery { repository.scanVideos(any()) } returns flowOf(*videos.toTypedArray())
        viewModel.startScanning()
        assertEquals(15, viewModel.videos.value.size)
    }

    @Test
    fun `empty scan leaves videos list empty`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flowOf()
        viewModel.startScanning()
        assertTrue(viewModel.videos.value.isEmpty())
    }

    @Test
    fun `isScanning becomes false after empty scan`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flowOf()
        viewModel.startScanning()
        assertFalse(viewModel.isScanning.value)
    }
}
