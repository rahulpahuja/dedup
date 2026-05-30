package com.rp.dedup.core.deepoptimization

import com.rp.dedup.core.model.EmptyFolder
import com.rp.dedup.core.model.EmptyFolderState
import com.rp.dedup.core.viewmodels.EmptyFolderViewModel
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

class EmptyFolderViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<EmptyFolderRepository>()
    private lateinit var viewModel: EmptyFolderViewModel

    @Before
    fun setUp() {
        viewModel = EmptyFolderViewModel(
            repository = repository,
            ioDispatcher = coroutineRule.testDispatcher
        )
    }

    private fun folder(name: String, path: String = "/sdcard/$name") =
        EmptyFolder(path = path, name = name, parentPath = "/sdcard")

    // --- initial state ---

    @Test
    fun `initial state is Idle`() {
        assertTrue(viewModel.state.value is EmptyFolderState.Idle)
    }

    // --- startScan ---

    @Test
    fun `startScan transitions to Results after repository returns`() = runTest {
        coEvery { repository.findEmptyFolders() } returns listOf(folder("empty1"))

        viewModel.startScan()

        assertTrue(viewModel.state.value is EmptyFolderState.Results)
    }

    @Test
    fun `startScan results contain all folders returned by repository`() = runTest {
        val folders = listOf(folder("a"), folder("b"), folder("c"))
        coEvery { repository.findEmptyFolders() } returns folders

        viewModel.startScan()

        val result = viewModel.state.value as EmptyFolderState.Results
        assertEquals(3, result.folders.size)
    }

    @Test
    fun `startScan with empty results yields empty Results`() = runTest {
        coEvery { repository.findEmptyFolders() } returns emptyList()

        viewModel.startScan()

        val result = viewModel.state.value as EmptyFolderState.Results
        assertTrue(result.folders.isEmpty())
    }

    @Test
    fun `startScan is idempotent while scanning`() = runTest {
        coEvery { repository.findEmptyFolders() } coAnswers {
            delay(Long.MAX_VALUE)
            emptyList()
        }

        viewModel.startScan()
        // First call is suspended inside repository; state == Scanning
        viewModel.startScan() // should be ignored

        coVerify(exactly = 1) { repository.findEmptyFolders() }
    }

    @Test
    fun `startScan transitions to Error on exception`() = runTest {
        coEvery { repository.findEmptyFolders() } throws SecurityException("No permission")

        viewModel.startScan()

        val error = viewModel.state.value as EmptyFolderState.Error
        assertEquals("No permission", error.message)
    }

    // --- deleteFolders ---

    @Test
    fun `deleteFolders removes successfully deleted folders from results`() = runTest {
        val f1 = folder("a")
        val f2 = folder("b")
        coEvery { repository.findEmptyFolders() } returns listOf(f1, f2)
        coEvery { repository.deleteFolder(f1) } returns true
        coEvery { repository.deleteFolder(f2) } returns false

        viewModel.startScan()
        viewModel.deleteFolders(listOf(f1, f2))

        val result = viewModel.state.value as EmptyFolderState.Results
        assertEquals(1, result.folders.size)
        assertEquals("b", result.folders.first().name)
    }

    @Test
    fun `deleteFolders with all successful leaves empty result list`() = runTest {
        val f1 = folder("a")
        val f2 = folder("b")
        coEvery { repository.findEmptyFolders() } returns listOf(f1, f2)
        coEvery { repository.deleteFolder(any()) } returns true

        viewModel.startScan()
        viewModel.deleteFolders(listOf(f1, f2))

        val result = viewModel.state.value as EmptyFolderState.Results
        assertTrue(result.folders.isEmpty())
    }

    @Test
    fun `deleteFolders does nothing when state is not Results`() = runTest {
        // State is still Idle — delete call should be safe no-op
        viewModel.deleteFolders(listOf(folder("a")))

        assertTrue(viewModel.state.value is EmptyFolderState.Idle)
        coVerify(exactly = 0) { repository.deleteFolder(any()) }
    }
}
