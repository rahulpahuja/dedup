package com.rp.dedup.core.deepoptimization

import com.rp.dedup.core.model.*
import com.rp.dedup.core.model.state.WhatsAppCleanerState
import com.rp.dedup.core.viewmodels.WhatsAppCleanerViewModel
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WhatsAppCleanerViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<WhatsAppCleanerRepository>()
    private lateinit var viewModel: WhatsAppCleanerViewModel

    @Before
    fun setUp() {
        viewModel = WhatsAppCleanerViewModel(
            repository    = repository,
            ioDispatcher  = coroutineRule.testDispatcher
        )
    }

    private fun file(name: String, folder: WhatsAppFolder): WhatsAppFile =
        WhatsAppFile(uri = mockk(relaxed = true), name = name, size = 1000L, path = name, folder = folder)

    private fun emptyResult() = WhatsAppScanResult(
        duplicateMedia    = emptyList(),
        duplicateStatuses = emptyList(),
        duplicateDocs     = emptyList(),
        largeFiles        = emptyList(),
        sentReceivedMatches = emptyList()
    )

    // ─── initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial state is Idle`() {
        assertTrue(viewModel.state.value is WhatsAppCleanerState.Idle)
    }

    // ─── startScan ────────────────────────────────────────────────────────────

    @Test
    fun `startScan transitions to Results on success`() = runTest {
        coEvery { repository.scanAll() } returns emptyResult()

        viewModel.startScan()

        assertTrue(viewModel.state.value is WhatsAppCleanerState.Results)
    }

    @Test
    fun `startScan transitions to Error on exception`() = runTest {
        coEvery { repository.scanAll() } throws SecurityException("No permission")

        viewModel.startScan()

        val error = viewModel.state.value as WhatsAppCleanerState.Error
        assertEquals("No permission", error.message)
    }

    @Test
    fun `startScan is idempotent while already scanning`() = runTest {
        coEvery { repository.scanAll() } coAnswers {
            delay(Long.MAX_VALUE)
            emptyResult()
        }

        viewModel.startScan()
        viewModel.startScan()   // second call should be ignored

        coVerify(exactly = 1) { repository.scanAll() }
    }

    @Test
    fun `startScan results contain data returned by repository`() = runTest {
        val f1 = file("a.jpg", WhatsAppFolder.IMAGES)
        val f2 = file("b.jpg", WhatsAppFolder.IMAGES)
        val result = emptyResult().copy(duplicateMedia = listOf(listOf(f1, f2)))
        coEvery { repository.scanAll() } returns result

        viewModel.startScan()

        val s = viewModel.state.value as WhatsAppCleanerState.Results
        assertEquals(1, s.data.duplicateMedia.size)
        assertEquals(2, s.data.duplicateMedia[0].size)
    }

    // ─── deleteFiles ─────────────────────────────────────────────────────────

    @Test
    fun `deleteFiles removes specified uris from large files`() = runTest {
        val f1 = file("big.mp4", WhatsAppFolder.VIDEOS)
        val f2 = file("huge.mp4", WhatsAppFolder.VIDEOS)
        val result = emptyResult().copy(largeFiles = listOf(f1, f2))
        coEvery { repository.scanAll() } returns result
        coEvery { repository.deleteFiles(any()) } returns 1

        viewModel.startScan()
        viewModel.deleteFiles(listOf(f1.uri))

        val s = viewModel.state.value as WhatsAppCleanerState.Results
        assertEquals(1, s.data.largeFiles.size)
        assertEquals("huge.mp4", s.data.largeFiles[0].name)
    }

    @Test
    fun `deleteFiles removes group that shrinks below 2 from duplicateMedia`() = runTest {
        val f1 = file("a.jpg", WhatsAppFolder.IMAGES)
        val f2 = file("b.jpg", WhatsAppFolder.IMAGES)
        val result = emptyResult().copy(duplicateMedia = listOf(listOf(f1, f2)))
        coEvery { repository.scanAll() } returns result
        coEvery { repository.deleteFiles(any()) } returns 1

        viewModel.startScan()
        viewModel.deleteFiles(listOf(f2.uri))

        val s = viewModel.state.value as WhatsAppCleanerState.Results
        assertTrue(s.data.duplicateMedia.isEmpty())
    }

    @Test
    fun `deleteFiles removes sentReceivedMatch when either file is deleted`() = runTest {
        val sent     = file("s.jpg", WhatsAppFolder.SENT_IMAGES)
        val received = file("r.jpg", WhatsAppFolder.IMAGES)
        val result = emptyResult().copy(sentReceivedMatches = listOf(SentReceivedMatch(sent, received)))
        coEvery { repository.scanAll() } returns result
        coEvery { repository.deleteFiles(any()) } returns 1

        viewModel.startScan()
        viewModel.deleteFiles(listOf(sent.uri))

        val s = viewModel.state.value as WhatsAppCleanerState.Results
        assertTrue(s.data.sentReceivedMatches.isEmpty())
    }

    @Test
    fun `deleteFiles does nothing when state is not Results`() = runTest {
        val f = file("x.jpg", WhatsAppFolder.IMAGES)
        viewModel.deleteFiles(listOf(f.uri))

        assertTrue(viewModel.state.value is WhatsAppCleanerState.Idle)
        coVerify(exactly = 0) { repository.deleteFiles(any()) }
    }
}
