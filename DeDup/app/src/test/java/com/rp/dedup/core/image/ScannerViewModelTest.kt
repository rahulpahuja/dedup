package com.rp.dedup.core.image

import android.content.Context
import com.rp.dedup.core.model.ScannedImage
import com.rp.dedup.core.repository.ImageScannerRepository
import com.rp.dedup.core.viewmodels.ScannerViewModel
import com.rp.dedup.core.image.BestShotAnalyzer
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScannerViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val context = mockk<Context>(relaxed = true)
    private val repository = mockk<ImageScannerRepository>()
    private lateinit var viewModel: ScannerViewModel

    @Before
    fun setUp() {
        mockkObject(BestShotAnalyzer)
        coEvery { BestShotAnalyzer.analyzeGroups(any(), any()) } answers { it.invocation.args[1] as List<List<ScannedImage>> }
        viewModel = ScannerViewModel(context, repository, defaultDispatcher = coroutineRule.testDispatcher)
    }

    @org.junit.After
    fun tearDown() {
        unmockkObject(BestShotAnalyzer)
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
    fun `startScanning sets isScanning to true while running`() = runTest {
        coEvery { repository.scanImagesInParallel(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        assertTrue(viewModel.isScanning.value)
    }

    @Test
    fun `startScanning clears previous duplicate groups`() = runTest {
        coEvery { repository.scanImagesInParallel(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `startScanning groups near-identical images together`() = runTest {
        // hamming distance between 0b0 and 0b1 is 1 — within threshold of 5
        val img1 = ScannedImage("content://1", 0b0L, 100L)
        val img2 = ScannedImage("content://2", 0b1L, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        assertEquals(1, viewModel.duplicateGroups.value.size)
        assertEquals(2, viewModel.duplicateGroups.value[0].size)
    }

    @Test
    fun `startScanning does not group perceptually different images`() = runTest {
        // hamming distance between 0 and Long.MAX_VALUE is 63 — far above threshold
        val img1 = ScannedImage("content://1", 0L, 100L)
        val img2 = ScannedImage("content://2", Long.MAX_VALUE, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        // Both groups have size 1, so neither appears in duplicateGroups
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `isScanning returns to false after scan completes`() = runTest {
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf()

        viewModel.startScanning()

        assertFalse(viewModel.isScanning.value)
    }

    // --- cancelScanning ---

    @Test
    fun `cancelScanning resets isScanning to false`() = runTest {
        coEvery { repository.scanImagesInParallel(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel.startScanning()
        assertTrue(viewModel.isScanning.value)
        viewModel.cancelScanning()
        assertFalse(viewModel.isScanning.value)
    }

    @Test
    fun `cancelScanning preserves any groups found before cancel`() = runTest {
        val img1 = ScannedImage("content://1", 0b0L, 100L)
        val img2 = ScannedImage("content://2", 0b1L, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        val groupsBefore = viewModel.duplicateGroups.value.size
        viewModel.cancelScanning()

        assertEquals(groupsBefore, viewModel.duplicateGroups.value.size)
    }

    // --- getAutoClearUris ---

    @Test
    fun `getAutoClearUris excludes first image in each group`() = runTest {
        val img1 = ScannedImage("content://keep", 0b0L, 100L)
        val img2 = ScannedImage("content://delete", 0b1L, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        val uris = viewModel.getAutoClearUris()
        assertFalse(uris.contains("content://keep"))
        assertTrue(uris.contains("content://delete"))
    }

    @Test
    fun `getAutoClearUris returns empty when no duplicate groups exist`() = runTest {
        val img1 = ScannedImage("content://1", 0L, 100L)
        val img2 = ScannedImage("content://2", Long.MAX_VALUE, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        assertTrue(viewModel.getAutoClearUris().isEmpty())
    }

    // --- removeDeletedImagesFromUI ---

    @Test
    fun `removeDeletedImagesFromUI with empty list leaves groups unchanged`() = runTest {
        val img1 = ScannedImage("content://1", 0b0L, 100L)
        val img2 = ScannedImage("content://2", 0b1L, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        val sizeBefore = viewModel.duplicateGroups.value.size
        viewModel.removeDeletedImagesFromUI(emptyList())
        assertEquals(sizeBefore, viewModel.duplicateGroups.value.size)
    }

    @Test
    fun `removeDeletedImagesFromUI removes group when it shrinks to one image`() = runTest {
        val img1 = ScannedImage("content://1", 0b0L, 100L)
        val img2 = ScannedImage("content://2", 0b1L, 200L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        viewModel.removeDeletedImagesFromUI(listOf("content://2"))

        // Group now has only img1 — filtered out of duplicateGroups
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `removeDeletedImagesFromUI on empty state is safe`() {
        viewModel.removeDeletedImagesFromUI(listOf("content://nonexistent"))
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }
}
