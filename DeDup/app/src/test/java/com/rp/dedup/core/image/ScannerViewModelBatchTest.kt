package com.rp.dedup.core.image

import android.content.Context
import com.rp.dedup.core.model.ScannedImage
import com.rp.dedup.core.repository.ImageScannerRepository
import com.rp.dedup.core.viewmodels.ScannerViewModel
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Focuses on the processBatch grouping logic in ScannerViewModel:
 *  - exact-byte key ("e:${sizeInBytes}_${exactHash}") takes priority
 *  - near-dup key ("d:${dHash}") used when exactHash == -1
 *  - exact groups are never expanded via dHash (prevents semantic corruption)
 *  - threshold enforcement, multiple simultaneous groups, etc.
 */
class ScannerViewModelBatchTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val context = mockk<Context>(relaxed = true)
    private val repository = mockk<ImageScannerRepository>()
    private lateinit var viewModel: ScannerViewModel

    @Before
    fun setUp() {
        mockkObject(BestShotAnalyzer)
        coEvery { BestShotAnalyzer.analyzeGroups(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            it.invocation.args[1] as List<List<ScannedImage>>
        }
        viewModel = ScannerViewModel(context, repository, defaultDispatcher = coroutineRule.testDispatcher)
    }

    @After
    fun tearDown() {
        unmockkObject(BestShotAnalyzer)
    }

    // ── exact-byte grouping ────────────────────────────────────────────────────

    @Test
    fun `two images with identical exactHash and size form one group`() = runTest {
        val img1 = ScannedImage("content://a", dHash = 0L, sizeInBytes = 1000L, exactHash = 42L)
        val img2 = ScannedImage("content://b", dHash = 1L, sizeInBytes = 1000L, exactHash = 42L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        assertEquals(1, viewModel.duplicateGroups.value.size)
        assertEquals(2, viewModel.duplicateGroups.value[0].size)
    }

    @Test
    fun `images with same exactHash but different sizes are NOT grouped`() = runTest {
        // Same CRC32 but different file size → different logical files (hash collision prevention)
        val img1 = ScannedImage("content://a", dHash = 0L, sizeInBytes = 1000L, exactHash = 42L)
        val img2 = ScannedImage("content://b", dHash = 0L, sizeInBytes = 2000L, exactHash = 42L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `three images with same exact key all land in one group`() = runTest {
        val img1 = ScannedImage("content://a", 0L, 500L, exactHash = 99L)
        val img2 = ScannedImage("content://b", 0L, 500L, exactHash = 99L)
        val img3 = ScannedImage("content://c", 0L, 500L, exactHash = 99L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2, img3)

        viewModel.startScanning()

        assertEquals(3, viewModel.duplicateGroups.value[0].size)
    }

    // ── near-duplicate (dHash) grouping ───────────────────────────────────────

    @Test
    fun `two images with exactHash -1 grouped by dHash when distance is within threshold`() = runTest {
        // Hamming distance 1 — within default threshold of 6
        val img1 = ScannedImage("content://a", dHash = 0b0L, sizeInBytes = 100L, exactHash = -1L)
        val img2 = ScannedImage("content://b", dHash = 0b1L, sizeInBytes = 200L, exactHash = -1L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        assertEquals(1, viewModel.duplicateGroups.value.size)
    }

    @Test
    fun `images with dHash distance above threshold form separate groups`() = runTest {
        // Hamming distance 32 — far above any threshold
        val img1 = ScannedImage("content://a", dHash = 0xFFFFFFFFL, sizeInBytes = 100L, exactHash = -1L)
        val img2 = ScannedImage("content://b", dHash = 0x00000000L, sizeInBytes = 200L, exactHash = -1L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()

        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    // ── exact groups are NOT expanded via dHash ────────────────────────────────

    @Test
    fun `near-dup image is NOT added to an exact-byte group even if dHash matches`() = runTest {
        // img1 and img2 form an exact group (same size + exactHash).
        // img3 has a very similar dHash but is NOT byte-identical.
        // img3 must NOT be pulled into the exact group.
        val img1 = ScannedImage("content://a", dHash = 0L, sizeInBytes = 1000L, exactHash = 7L)
        val img2 = ScannedImage("content://b", dHash = 0L, sizeInBytes = 1000L, exactHash = 7L)
        val img3 = ScannedImage("content://c", dHash = 0b1L, sizeInBytes = 999L, exactHash = -1L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2, img3)

        viewModel.startScanning()

        // img1+img2 form one group of 2; img3 forms its own group of 1 (not in duplicates)
        val groups = viewModel.duplicateGroups.value
        assertEquals("Only one group should exist — the exact-byte pair", 1, groups.size)
        assertEquals("Exact group must have exactly 2 members", 2, groups[0].size)
        assertFalse("img3 must not be in the exact group", groups[0].any { it.uri == "content://c" })
    }

    // ── two independent groups in same scan ────────────────────────────────────

    @Test
    fun `two independent duplicate pairs produce two separate groups`() = runTest {
        val a1 = ScannedImage("content://a1", dHash = 0L, sizeInBytes = 100L, exactHash = 1L)
        val a2 = ScannedImage("content://a2", dHash = 0L, sizeInBytes = 100L, exactHash = 1L)
        val b1 = ScannedImage("content://b1", dHash = 0L, sizeInBytes = 200L, exactHash = 2L)
        val b2 = ScannedImage("content://b2", dHash = 0L, sizeInBytes = 200L, exactHash = 2L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(a1, a2, b1, b2)

        viewModel.startScanning()

        assertEquals(2, viewModel.duplicateGroups.value.size)
    }

    // ── single image never appears in duplicateGroups ──────────────────────────

    @Test
    fun `single unique image does not appear in duplicateGroups`() = runTest {
        val img = ScannedImage("content://unique", dHash = 0L, sizeInBytes = 100L, exactHash = -1L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img)

        viewModel.startScanning()

        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    // ── idempotent startScanning ───────────────────────────────────────────────

    @Test
    fun `calling startScanning while already scanning is idempotent`() = runTest {
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf()

        viewModel.startScanning()
        val firstResult = viewModel.duplicateGroups.value

        viewModel.startScanning()
        assertEquals(firstResult, viewModel.duplicateGroups.value)
    }

    // ── getAutoClearUris — exact groups ───────────────────────────────────────

    @Test
    fun `getAutoClearUris returns all but first from each group`() = runTest {
        val img1 = ScannedImage("content://keep", dHash = 0L, sizeInBytes = 100L, exactHash = 5L)
        val img2 = ScannedImage("content://del1", dHash = 0L, sizeInBytes = 100L, exactHash = 5L)
        val img3 = ScannedImage("content://del2", dHash = 0L, sizeInBytes = 100L, exactHash = 5L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2, img3)

        viewModel.startScanning()

        val uris = viewModel.getAutoClearUris()
        assertEquals(2, uris.size)
        assertFalse(uris.contains("content://keep"))
    }

    // ── removeDeletedImagesFromUI — group collapse ────────────────────────────

    @Test
    fun `removeDeletedImagesFromUI collapses group that drops below 2 members`() = runTest {
        val img1 = ScannedImage("content://a", dHash = 0L, sizeInBytes = 100L, exactHash = 3L)
        val img2 = ScannedImage("content://b", dHash = 0L, sizeInBytes = 100L, exactHash = 3L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2)

        viewModel.startScanning()
        assertEquals(1, viewModel.duplicateGroups.value.size)

        viewModel.removeDeletedImagesFromUI(listOf("content://b"))
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `removeDeletedImagesFromUI keeps group with 3 members after removing 1`() = runTest {
        val img1 = ScannedImage("content://a", dHash = 0L, sizeInBytes = 100L, exactHash = 4L)
        val img2 = ScannedImage("content://b", dHash = 0L, sizeInBytes = 100L, exactHash = 4L)
        val img3 = ScannedImage("content://c", dHash = 0L, sizeInBytes = 100L, exactHash = 4L)
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf(img1, img2, img3)

        viewModel.startScanning()
        viewModel.removeDeletedImagesFromUI(listOf("content://c"))

        assertEquals(1, viewModel.duplicateGroups.value.size)
        assertEquals(2, viewModel.duplicateGroups.value[0].size)
    }

    // ── empty scan ─────────────────────────────────────────────────────────────

    @Test
    fun `empty repository leaves duplicateGroups empty`() = runTest {
        coEvery { repository.scanImagesInParallel(any()) } returns flowOf()

        viewModel.startScanning()

        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }
}
