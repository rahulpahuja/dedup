package com.rp.dedup.core.viewmodels

import android.net.Uri
import com.rp.dedup.core.dao.ImageEmbeddingDao
import com.rp.dedup.core.repository.MemoriesRepository
import com.rp.dedup.core.repository.MemoryGroup
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric-based: MemoriesViewModel.load() calls the real android.net.Uri.parse(). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = com.rp.dedup.util.TestApp::class)
class MemoriesViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val dao = mockk<ImageEmbeddingDao>(relaxed = true)
    private val repository = mockk<MemoriesRepository>(relaxed = true)

    @Test
    fun `initial memories state is empty`() {
        coEvery { dao.getAllUris() } returns emptyList()

        val viewModel = MemoriesViewModel(dao, repository)

        assertTrue(viewModel.memories.value.isEmpty())
    }

    @Test
    fun `load populates memories from repository`() = runTest {
        val uri = mockk<Uri>(relaxed = true)
        coEvery { dao.getAllUris() } returns listOf("content://media/1")
        coEvery { repository.findMemories(any(), any()) } returns listOf(MemoryGroup(1, listOf(uri)))

        val viewModel = MemoriesViewModel(dao, repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.memories.value.size)
        assertEquals(1, viewModel.memories.value.first().yearsAgo)
    }

    @Test
    fun `load sets isLoading false once complete`() = runTest {
        coEvery { dao.getAllUris() } returns emptyList()
        coEvery { repository.findMemories(any(), any()) } returns emptyList()

        val viewModel = MemoriesViewModel(dao, repository)
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `load with no indexed photos yields empty memories`() = runTest {
        coEvery { dao.getAllUris() } returns emptyList()
        coEvery { repository.findMemories(emptyList(), any()) } returns emptyList()

        val viewModel = MemoriesViewModel(dao, repository)
        advanceUntilIdle()

        assertTrue(viewModel.memories.value.isEmpty())
    }
}
