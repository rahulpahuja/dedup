package com.rp.dedup.core

import android.net.Uri
import com.rp.dedup.core.model.ScannedVideo
import com.rp.dedup.core.repository.VideoScannerRepository
import com.rp.dedup.core.viewmodels.VideoScannerViewModel
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VideoScannerViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<VideoScannerRepository>()
    private lateinit var viewModel: VideoScannerViewModel

    private fun fakeVideo(id: Int) = ScannedVideo(
        uri = mockk<Uri>(),
        name = "video$id.mp4",
        sizeInBytes = id * 1_000L,
        durationMs = id * 1_000L,
        mimeType = "video/mp4",
        path = "/storage/emulated/0/Movies/video$id.mp4"
    )

    @Before
    fun setUp() {
        viewModel = VideoScannerViewModel(repository, defaultDispatcher = coroutineRule.testDispatcher)
    }

    // --- Initial state ---

    @Test
    fun `initial state — not scanning`() {
        assertFalse(viewModel.isScanning.value)
    }

    @Test
    fun `initial state — no videos`() {
        assertTrue(viewModel.videos.value.isEmpty())
    }

    // --- startScanning ---

    @Test
    fun `startScanning sets isScanning to true`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        assertTrue(viewModel.isScanning.value)
    }

    @Test
    fun `startScanning clears videos list before scan`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        assertTrue(viewModel.videos.value.isEmpty())
    }

    @Test
    fun `startScanning is idempotent — second call ignored while scanning`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        viewModel.startScanning()
        coVerify(exactly = 1) { repository.scanVideos(any()) }
    }

    @Test
    fun `startScanning emits all videos when fewer than batch size`() = runTest {
        val videos = (1..10).map { fakeVideo(it) }
        coEvery { repository.scanVideos(any()) } returns flowOf(*videos.toTypedArray())

        viewModel.startScanning()

        assertEquals(10, viewModel.videos.value.size)
    }

    @Test
    fun `startScanning emits all videos across multiple batches`() = runTest {
        // BATCH_SIZE = 50; emit 75 to cross two batch boundaries
        // Note: VideoScannerViewModel uses 10 as batch size for updates
        val videos = (1..75).map { fakeVideo(it) }
        coEvery { repository.scanVideos(any()) } returns flowOf(*videos.toTypedArray())

        viewModel.startScanning()

        assertEquals(75, viewModel.videos.value.size)
    }

    @Test
    fun `startScanning emits empty list when repository returns nothing`() = runTest {
        coEvery { repository.scanVideos(any()) } returns emptyFlow()
        viewModel.startScanning()
        assertTrue(viewModel.videos.value.isEmpty())
    }

    @Test
    fun `isScanning becomes false after scan completes`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flowOf(fakeVideo(1))
        viewModel.startScanning()
        assertFalse(viewModel.isScanning.value)
    }

    // --- cancelScanning ---

    @Test
    fun `cancelScanning resets isScanning to false`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flow {
            delay(Long.MAX_VALUE)
        }
        viewModel.startScanning()
        assertTrue(viewModel.isScanning.value)
        viewModel.cancelScanning()
        assertFalse(viewModel.isScanning.value)
    }

    @Test
    fun `cancelScanning on idle ViewModel is safe`() {
        viewModel.cancelScanning()
        assertFalse(viewModel.isScanning.value)
    }

    @Test
    fun `after cancel a new scan can be started`() = runTest {
        coEvery { repository.scanVideos(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        assertTrue(viewModel.isScanning.value)
        viewModel.cancelScanning()
        assertFalse(viewModel.isScanning.value)

        coEvery { repository.scanVideos(any()) } returns flowOf(fakeVideo(1))
        viewModel.startScanning()
        assertEquals(1, viewModel.videos.value.size)
    }

    private fun customVideo(id: Int, size: Long, duration: Long) = ScannedVideo(
        uri = mockk<Uri>(),
        name = "video$id.mp4",
        sizeInBytes = size,
        durationMs = duration,
        mimeType = "video/mp4",
        path = "/storage/emulated/0/Movies/video$id.mp4"
    )

    @Test
    fun `findDuplicates groups videos with identical size`() = runTest {
        val v1 = customVideo(1, size = 1000L, duration = 5000L)
        val v2 = customVideo(2, size = 1000L, duration = 5500L)
        
        coEvery { repository.scanVideos(any()) } returns flowOf(v1, v2)
        viewModel.startScanning()
        
        val groups = viewModel.duplicateGroups.value
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].size)
        assertTrue(groups[0].any { it.name == v1.name })
        assertTrue(groups[0].any { it.name == v2.name })
    }

    @Test
    fun `findDuplicates does not group videos with different sizes`() = runTest {
        val v1 = customVideo(1, size = 1000L, duration = 5000L)
        val v2 = customVideo(2, size = 2000L, duration = 5000L)
        
        coEvery { repository.scanVideos(any()) } returns flowOf(v1, v2)
        viewModel.startScanning()
        
        val groups = viewModel.duplicateGroups.value
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `findDuplicates groups videos with invalid durations if size matches`() = runTest {
        val v1 = customVideo(1, size = 1000L, duration = 0L)
        val v2 = customVideo(2, size = 1000L, duration = 0L)
        
        coEvery { repository.scanVideos(any()) } returns flowOf(v1, v2)
        viewModel.startScanning()
        
        val groups = viewModel.duplicateGroups.value
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].size)
    }
}
