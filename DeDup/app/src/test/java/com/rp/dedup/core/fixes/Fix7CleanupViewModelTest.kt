package com.rp.dedup.core.fixes

import com.rp.dedup.core.model.ScannedFile
import com.rp.dedup.core.repository.FileScannerRepository
import com.rp.dedup.core.viewmodels.CleanupViewModel
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import android.net.Uri
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Fix #7 — CleanupViewModel.refreshAll() launched 4 concurrent coroutines that each
 * did read-modify-write on _uiState without synchronisation. The last coroutine to write
 * would overwrite the others, causing lost scan results.
 *
 * The fix uses a Mutex to serialise every _uiState update.
 */
class Fix7CleanupViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<FileScannerRepository>()

    private fun fakeFile(name: String, size: Long) = ScannedFile(
        uri = mockk<Uri>(),
        name = name,
        size = size,
        path = "/storage/$name",
        extension = name.substringAfterLast('.')
    )

    @Before
    fun setUp() {
        // ViewModel calls scanFilesByExtension with only the extensions list (defaults used)
        every { repository.scanFilesByExtension(any()) } returns emptyFlow()
        every { repository.scanOldFiles(any(), any()) } returns emptyFlow()
    }

    @Test
    fun `after scans complete all categories have isLoading false`() = runTest {
        val vm = CleanupViewModel(repository)
        advanceUntilIdle() // let init coroutines finish
        val state = vm.uiState.value
        assertFalse(state.videoStats.isLoading)
        assertFalse(state.archiveStats.isLoading)
        assertFalse(state.appDownloadStats.isLoading)
        assertFalse(state.oldDownloadStats.isLoading)
    }

    @Test
    fun `video scan results are preserved when archives scan also completes`() = runTest {
        val largeVideo = fakeFile("movie.mp4", 50 * 1024 * 1024L)
        val archive = fakeFile("backup.zip", 1024L)

        every { repository.scanFilesByExtension(match { "mp4" in it }) } returns flowOf(largeVideo)
        every { repository.scanFilesByExtension(match { "zip" in it }) } returns flowOf(archive)

        val vm = CleanupViewModel(repository)
        advanceUntilIdle()

        assertEquals(
            "Video scan result must be preserved after archive scan completes",
            1, vm.uiState.value.videoStats.count
        )
        assertEquals(
            "Archive scan result must be preserved after video scan completes",
            1, vm.uiState.value.archiveStats.count
        )
    }

    @Test
    fun `all four categories have correct counts after concurrent scans`() = runTest {
        val video   = fakeFile("v.mp4", 20 * 1024 * 1024L)
        val archive = fakeFile("a.zip", 512L)
        val apk     = fakeFile("app.apk", 5 * 1024 * 1024L)

        every { repository.scanFilesByExtension(match { "mp4" in it }) } returns flowOf(video)
        every { repository.scanFilesByExtension(match { "zip" in it }) } returns flowOf(archive)
        every { repository.scanFilesByExtension(match { "apk" in it }) } returns flowOf(apk)
        every { repository.scanOldFiles(any(), any()) } returns emptyFlow()

        val vm = CleanupViewModel(repository)
        advanceUntilIdle()
        val state = vm.uiState.value

        assertEquals(1, state.videoStats.count)
        assertEquals(1, state.archiveStats.count)
        assertEquals(1, state.appDownloadStats.count)
        assertEquals(0, state.oldDownloadStats.count)
    }

    @Test
    fun `small videos below 10MB threshold are excluded from video stats`() = runTest {
        val smallVideo = fakeFile("clip.mp4", 5 * 1024 * 1024L) // 5 MB — below 10 MB threshold
        every { repository.scanFilesByExtension(match { "mp4" in it }) } returns flowOf(smallVideo)

        val vm = CleanupViewModel(repository)
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.videoStats.count)
    }

    @Test
    fun `stateMutex is declared as a field (structural check)`() {
        val field = CleanupViewModel::class.java.declaredFields.find { it.name == "stateMutex" }
        assertNotNull("stateMutex field must exist to prevent concurrent state races", field)
    }
}
