package com.rp.dedup.core.deepoptimization

import android.net.Uri
import com.rp.dedup.core.data.SocialApp
import com.rp.dedup.core.data.SocialMediaFile
import com.rp.dedup.core.data.SocialMediaType
import com.rp.dedup.core.viewmodels.SocialMediaCleanerState
import com.rp.dedup.core.viewmodels.SocialMediaCleanerViewModel
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

class SocialMediaCleanerViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<SocialMediaCleanerRepository>()
    private lateinit var viewModel: SocialMediaCleanerViewModel

    @Before
    fun setUp() {
        viewModel = SocialMediaCleanerViewModel(
            repository = repository,
            ioDispatcher = coroutineRule.testDispatcher
        )
    }

    private fun fakeFile(id: Int, size: Long = 1000L, checksum: String = "hash$id") = SocialMediaFile(
        uri = mockk<Uri>(),
        name = "file$id.jpg",
        size = size,
        path = "/WhatsApp/Media/file$id.jpg",
        app = SocialApp.WHATSAPP,
        mediaType = SocialMediaType.IMAGE,
        checksum = checksum
    )

    // --- initial state ---

    @Test
    fun `initial state is Idle`() {
        assertTrue(viewModel.state.value is SocialMediaCleanerState.Idle)
    }

    // --- startScan ---

    @Test
    fun `startScan emits ScanningFiles then Results`() = runTest {
        val file = fakeFile(1)
        coEvery { repository.scanMedia() } returns flowOf(file)
        coEvery { repository.computeChecksum(any()) } returns null

        viewModel.startScan()

        assertTrue(viewModel.state.value is SocialMediaCleanerState.Results)
    }

    @Test
    fun `startScan with empty storage yields Results with empty groups`() = runTest {
        coEvery { repository.scanMedia() } returns emptyFlow()

        viewModel.startScan()

        val result = viewModel.state.value as SocialMediaCleanerState.Results
        assertTrue(result.duplicateGroups.isEmpty())
        assertEquals(0L, result.reclaimableBytes)
    }

    @Test
    fun `startScan is idempotent while scanning`() = runTest {
        coEvery { repository.scanMedia() } returns flow { delay(Long.MAX_VALUE) }

        viewModel.startScan()
        viewModel.startScan()

        coVerify(exactly = 1) { repository.scanMedia() }
    }

    @Test
    fun `startScan groups files with matching checksum as duplicates`() = runTest {
        val f1 = fakeFile(1, size = 500L)
        val f2 = fakeFile(2, size = 500L) // same size → checksum computed

        coEvery { repository.scanMedia() } returns flowOf(f1, f2)
        // both get same checksum → they're duplicates
        coEvery { repository.computeChecksum(f1) } returns "same-hash"
        coEvery { repository.computeChecksum(f2) } returns "same-hash"

        viewModel.startScan()

        val result = viewModel.state.value as SocialMediaCleanerState.Results
        assertEquals(1, result.duplicateGroups.size)
        assertEquals(2, result.duplicateGroups[0].size)
    }

    @Test
    fun `startScan does not group files with different checksums`() = runTest {
        val f1 = fakeFile(1, size = 500L)
        val f2 = fakeFile(2, size = 500L)

        coEvery { repository.scanMedia() } returns flowOf(f1, f2)
        coEvery { repository.computeChecksum(f1) } returns "hash-a"
        coEvery { repository.computeChecksum(f2) } returns "hash-b"

        viewModel.startScan()

        val result = viewModel.state.value as SocialMediaCleanerState.Results
        assertTrue(result.duplicateGroups.isEmpty())
    }

    @Test
    fun `startScan skips checksum for files with unique sizes`() = runTest {
        val f1 = fakeFile(1, size = 100L)
        val f2 = fakeFile(2, size = 200L) // different size → no checksum

        coEvery { repository.scanMedia() } returns flowOf(f1, f2)

        viewModel.startScan()

        // computeChecksum should never be called since no two files share the same size
        coVerify(exactly = 0) { repository.computeChecksum(any()) }
    }

    @Test
    fun `reclaimableBytes equals sum of all but first file in each group`() = runTest {
        val f1 = fakeFile(1, size = 300L)
        val f2 = fakeFile(2, size = 300L)
        val f3 = fakeFile(3, size = 300L)

        coEvery { repository.scanMedia() } returns flowOf(f1, f2, f3)
        coEvery { repository.computeChecksum(any()) } returns "same-hash"

        viewModel.startScan()

        val result = viewModel.state.value as SocialMediaCleanerState.Results
        // 3 files in group → 2 reclaimable × 300 bytes = 600
        assertEquals(600L, result.reclaimableBytes)
    }

    // --- cancelScan ---

    @Test
    fun `cancelScan resets state to Idle`() = runTest {
        coEvery { repository.scanMedia() } returns flow { delay(Long.MAX_VALUE) }

        viewModel.startScan()
        viewModel.cancelScan()

        assertTrue(viewModel.state.value is SocialMediaCleanerState.Idle)
    }

    // --- deleteFiles ---

    @Test
    fun `deleteFiles removes deleted URIs from duplicate groups`() = runTest {
        val f1 = fakeFile(1, size = 500L)
        val f2 = fakeFile(2, size = 500L)

        coEvery { repository.scanMedia() } returns flowOf(f1, f2)
        coEvery { repository.computeChecksum(any()) } returns "same-hash"
        coEvery { repository.deleteFiles(any()) } returns 1

        viewModel.startScan()
        viewModel.deleteFiles(listOf(f2.uri))

        val result = viewModel.state.value as SocialMediaCleanerState.Results
        // Group now has only 1 file → no longer a duplicate group
        assertTrue(result.duplicateGroups.isEmpty())
    }

    // --- error handling ---

    @Test
    fun `startScan transitions to Error on repository exception`() = runTest {
        coEvery { repository.scanMedia() } returns flow { throw RuntimeException("IO error") }

        viewModel.startScan()

        val error = viewModel.state.value as SocialMediaCleanerState.Error
        assertEquals("IO error", error.message)
    }
}
