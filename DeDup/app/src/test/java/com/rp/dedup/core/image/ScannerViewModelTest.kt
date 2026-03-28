package com.rp.dedup.core.image

import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScannerViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<ImageScannerRepository>()
    private lateinit var viewModel: ScannerViewModel

    @Before
    fun setUp() {
        viewModel = ScannerViewModel(repository)
    }

    // --- Initial state ---

    @Test
    fun `initial state — not scanning`() {
        assertFalse(viewModel.isScanning.value)
    }

    @Test
    fun `initial state — no duplicate groups`() {
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `getAutoClearUris returns empty list when no groups`() {
        assertTrue(viewModel.getAutoClearUris().isEmpty())
    }

    // --- startScanning ---

    @Test
    fun `startScanning sets isScanning to true while running`() {
        coEvery { repository.scanImagesInParallel(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        assertTrue(viewModel.isScanning.value)
    }

    @Test
    fun `startScanning clears previous duplicate groups`() {
        coEvery { repository.scanImagesInParallel(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `startScanning groups near-identical images together`() = runBlocking {
        // hamming distance between 0b0 and 0b1 is 1 — within threshold of 5
        val img1 = ScannedImage("content://1", 0b0L, 100L)
        val img2 = ScannedImage("content://2", 0b1L, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()
        waitForScanToFinish()

        assertEquals(1, viewModel.duplicateGroups.value.size)
        assertEquals(2, viewModel.duplicateGroups.value[0].size)
    }

    @Test
    fun `startScanning does not group perceptually different images`() = runBlocking {
        // hamming distance between 0 and Long.MAX_VALUE is 63 — far above threshold
        val img1 = ScannedImage("content://1", 0L, 100L)
        val img2 = ScannedImage("content://2", Long.MAX_VALUE, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()
        waitForScanToFinish()

        // Both groups have size 1, so neither appears in duplicateGroups
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `isScanning returns to false after scan completes`() = runBlocking {
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf()

        viewModel.startScanning()
        waitForScanToFinish()

        assertFalse(viewModel.isScanning.value)
    }

    // --- cancelScanning ---

    @Test
    fun `cancelScanning resets isScanning to false`() {
        coEvery { repository.scanImagesInParallel(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        viewModel.cancelScanning()
        assertFalse(viewModel.isScanning.value)
    }

    @Test
    fun `cancelScanning preserves any groups found before cancel`() = runBlocking {
        val img1 = ScannedImage("content://1", 0b0L, 100L)
        val img2 = ScannedImage("content://2", 0b1L, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()
        waitForScanToFinish()

        val groupsBefore = viewModel.duplicateGroups.value.size
        viewModel.cancelScanning()

        assertEquals(groupsBefore, viewModel.duplicateGroups.value.size)
    }

    // --- getAutoClearUris ---

    @Test
    fun `getAutoClearUris excludes first image in each group`() = runBlocking {
        val img1 = ScannedImage("content://keep", 0b0L, 100L)
        val img2 = ScannedImage("content://delete", 0b1L, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()
        waitForScanToFinish()

        val uris = viewModel.getAutoClearUris()
        assertFalse(uris.contains("content://keep"))
        assertTrue(uris.contains("content://delete"))
    }

    @Test
    fun `getAutoClearUris returns empty when no duplicate groups exist`() = runBlocking {
        val img1 = ScannedImage("content://1", 0L, 100L)
        val img2 = ScannedImage("content://2", Long.MAX_VALUE, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()
        waitForScanToFinish()

        assertTrue(viewModel.getAutoClearUris().isEmpty())
    }

    // --- removeDeletedImagesFromUI ---

    @Test
    fun `removeDeletedImagesFromUI with empty list leaves groups unchanged`() = runBlocking {
        val img1 = ScannedImage("content://1", 0b0L, 100L)
        val img2 = ScannedImage("content://2", 0b1L, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()
        waitForScanToFinish()

        val sizeBefore = viewModel.duplicateGroups.value.size
        viewModel.removeDeletedImagesFromUI(emptyList())
        assertEquals(sizeBefore, viewModel.duplicateGroups.value.size)
    }

    @Test
    fun `removeDeletedImagesFromUI removes group when it shrinks to one image`() = runBlocking {
        val img1 = ScannedImage("content://1", 0b0L, 100L)
        val img2 = ScannedImage("content://2", 0b1L, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()
        waitForScanToFinish()

        viewModel.removeDeletedImagesFromUI(listOf("content://2"))

        // Group now has only img1 — filtered out of duplicateGroups
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `removeDeletedImagesFromUI on empty state is safe`() {
        viewModel.removeDeletedImagesFromUI(listOf("content://nonexistent"))
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    // --- Helpers ---

    /** Polls until the scan job completes (max 2 s). Works around Dispatchers.Default in prod code. */
    private suspend fun waitForScanToFinish(timeoutMs: Long = 2000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (viewModel.isScanning.value && System.currentTimeMillis() < deadline) {
            delay(20)
        }
    }
}
