package com.rp.dedup.core.viewmodels

import android.net.Uri
import com.rp.dedup.core.search.ImageSearchRepository
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageSearchViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<ImageSearchRepository>(relaxed = true)
    private lateinit var viewModel: ImageSearchViewModel

    private fun result(label: String) = ImageSearchRepository.SearchResult(
        uri           = mockk<Uri>(),
        matchedLabels = listOf(label)
    )

    @Before
    fun setUp() {
        viewModel = ImageSearchViewModel(repository)
    }

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty results and not searching`() {
        assertTrue(viewModel.results.value.isEmpty())
        assertFalse(viewModel.isSearching.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `initial progress is zero`() {
        assertEquals(0 to 0, viewModel.progress.value)
    }

    // ── search with blank query ────────────────────────────────────────────────

    @Test
    fun `search with blank query clears results without searching`() = runTest {
        viewModel.search("   ")
        advanceUntilIdle()
        assertTrue(viewModel.results.value.isEmpty())
        assertFalse(viewModel.isSearching.value)
    }

    @Test
    fun `search with empty string clears results`() = runTest {
        viewModel.search("")
        advanceUntilIdle()
        assertTrue(viewModel.results.value.isEmpty())
    }

    // ── clear ──────────────────────────────────────────────────────────────────

    @Test
    fun `clear resets results to empty`() {
        viewModel.clear()
        assertTrue(viewModel.results.value.isEmpty())
    }

    @Test
    fun `clear resets isSearching to false`() {
        viewModel.clear()
        assertFalse(viewModel.isSearching.value)
    }

    @Test
    fun `clear resets progress to zero`() {
        viewModel.clear()
        assertEquals(0 to 0, viewModel.progress.value)
    }

    @Test
    fun `clear resets error to null`() {
        viewModel.clear()
        assertNull(viewModel.error.value)
    }

    // ── search — verified against repository stub on Main dispatcher ───────────

    @Test
    fun `search non-blank query sets isSearching briefly then false`() = runTest {
        coEvery { repository.search(any(), any()) } returns emptyList()
        viewModel.search("cats")
        advanceUntilIdle()
        // After completion isSearching is false
        assertFalse(viewModel.isSearching.value)
    }

    @Test
    fun `search returns empty list from repository`() = runTest {
        coEvery { repository.search(any(), any()) } returns emptyList()
        viewModel.search("no-results")
        advanceUntilIdle()
        assertTrue(viewModel.results.value.isEmpty())
    }
}
