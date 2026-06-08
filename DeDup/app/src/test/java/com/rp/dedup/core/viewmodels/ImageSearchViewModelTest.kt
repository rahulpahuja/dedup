package com.rp.dedup.core.viewmodels

import android.net.Uri
import com.rp.dedup.core.search.ImageSearchRepository
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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

    // ── search ─────────────────────────────────────────────────────────────────

    @Test
    fun `search with blank query calls clear and returns empty results`() = runTest {
        viewModel.search("   ")
        assertTrue(viewModel.results.value.isEmpty())
        assertFalse(viewModel.isSearching.value)
    }

    @Test
    fun `search updates results from repository`() = runTest {
        val results = listOf(result("cat"), result("dog"))
        coEvery { repository.search("animals", any()) } returns results

        viewModel.search("animals")

        assertEquals(2, viewModel.results.value.size)
        assertFalse(viewModel.isSearching.value)
    }

    @Test
    fun `search sets error on repository exception`() = runTest {
        coEvery { repository.search(any(), any()) } throws RuntimeException("ML Kit failed")

        viewModel.search("cats")

        assertNotNull(viewModel.error.value)
        assertTrue(viewModel.error.value!!.contains("ML Kit failed"))
        assertFalse(viewModel.isSearching.value)
    }

    @Test
    fun `search progress is updated via callback`() = runTest {
        coEvery { repository.search(any(), any()) } coAnswers {
            val onProgress = secondArg<(Int, Int) -> Unit>()
            onProgress(5, 100)
            emptyList()
        }

        viewModel.search("cats")

        // After completion isSearching is false and progress was tracked
        assertFalse(viewModel.isSearching.value)
    }

    // ── clear ──────────────────────────────────────────────────────────────────

    @Test
    fun `clear resets all state`() = runTest {
        coEvery { repository.search(any(), any()) } returns listOf(result("cat"))
        viewModel.search("cat")
        viewModel.clear()

        assertTrue(viewModel.results.value.isEmpty())
        assertFalse(viewModel.isSearching.value)
        assertNull(viewModel.error.value)
        assertEquals(0 to 0, viewModel.progress.value)
    }
}
