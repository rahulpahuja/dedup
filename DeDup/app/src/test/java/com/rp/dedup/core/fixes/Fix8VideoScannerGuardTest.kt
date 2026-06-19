package com.rp.dedup.core.fixes

import com.rp.dedup.core.model.ScannedVideo
import com.rp.dedup.core.repository.VideoScannerRepository
import com.rp.dedup.core.viewmodels.VideoScannerViewModel
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Fix #8 — VideoScannerViewModel.startScanning() used to set _isScanning = true INSIDE
 * the launched coroutine. This created a race: between scanJob assignment and the
 * coroutine reaching _isScanning = true, a second call to startScanning() could pass
 * the guard and launch a duplicate scan.
 *
 * The fix: _isScanning.value = true is now set synchronously BEFORE viewModelScope.launch().
 */
class Fix8VideoScannerGuardTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<VideoScannerRepository>()
    private lateinit var viewModel: VideoScannerViewModel

    @Before
    fun setUp() {
        coEvery { repository.scanVideos(any()) } returns flow { delay(Long.MAX_VALUE) }
        viewModel = VideoScannerViewModel(repository, defaultDispatcher = coroutineRule.testDispatcher)
    }

    @Test
    fun `isScanning is true immediately after startScanning returns before any coroutine yields`() = runTest {
        viewModel.startScanning()
        assertTrue(
            "_isScanning must be true synchronously so re-entrant calls are blocked immediately",
            viewModel.isScanning.value
        )
    }

    @Test
    fun `second startScanning call while scanning is running is ignored`() = runTest {
        viewModel.startScanning()
        assertTrue(viewModel.isScanning.value)

        // Without the fix, _isScanning was set inside the coroutine; a second call
        // before that point could slip past the guard. With the fix, it cannot.
        viewModel.startScanning()

        coVerify(exactly = 1) { repository.scanVideos(any()) }
    }

    // Note: testing that _isScanning resets to false after cancel requires
    // replacing Dispatchers.IO in tests (the finally block uses NonCancellable + IO).
    // That is a pre-existing test-infrastructure gap tracked separately.
    // The core invariant — the guard is set synchronously before launch — is covered above.
}
