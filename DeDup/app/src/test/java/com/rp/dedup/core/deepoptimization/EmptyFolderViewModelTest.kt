package com.rp.dedup.core.deepoptimization

import android.net.Uri
import com.rp.dedup.core.model.EmptyFolder
import com.rp.dedup.core.model.state.EmptyFolderState
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
            repository    = repository,
            ioDispatcher  = coroutineRule.testDispatcher
        )
    }

    private fun folder(name: String, path: String = "/sdcard/$name") =
        EmptyFolder(path = path, name = name, parentPath = "/sdcard")

    @Test
    fun `initial state is Idle`() {
        assertTrue(viewModel.state.value is EmptyFolderState.Idle)
    }

    @Test
    fun `startScan transitions to Results after repository returns`() = runTest {
        coEvery { repository.findEmptyFolders(any()) } returns listOf(folder("empty1"))
        viewModel.startScan()
        assertTrue(viewModel.state.value is EmptyFolderState.Results)
    }

    @Test
    fun `startScan results contain all folders returned by repository`() = runTest {
        val folders = listOf(folder("a"), folder("b"), folder("c"))
        coEvery { repository.findEmptyFolders(any()) } returns folders
        viewModel.startScan()
        val result = viewModel.state.value as EmptyFolderState.Results
        assertEquals(3, result.folders.size)
    }

    @Test
    fun `startScan with empty results yields empty Results`() = runTest {
        coEvery { repository.findEmptyFolders(any()) } returns emptyList()
        viewModel.startScan()
        val result = viewModel.state.value as EmptyFolderState.Results
        assertTrue(result.folders.isEmpty())
    }

    @Test
    fun `startScan passes treeUri to repository`() = runTest {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")
        coEvery { repository.findEmptyFolders(uri) } returns emptyList()
        viewModel.startScan(treeUri = uri)
        coVerify(exactly = 1) { repository.findEmptyFolders(uri) }
    }

    @Test
    fun `startScan passes null treeUri when called with no argument`() = runTest {
        coEvery { repository.findEmptyFolders(null) } returns emptyList()
        viewModel.startScan()
        coVerify(exactly = 1) { repository.findEmptyFolders(null) }
    }

    @Test
    fun `startScan is idempotent while scanning`() = runTest {
        coEvery { repository.findEmptyFolders(any()) } coAnswers {
            delay(Long.MAX_VALUE)
            emptyList()
        }
        viewModel.startScan()
        viewModel.startScan()
        coVerify(exactly = 1) { repository.findEmptyFolders(any()) }
    }

    @Test
    fun `startScan transitions to Error on exception`() = runTest {
        coEvery { repository.findEmptyFolders(any()) } throws SecurityException("No permission")
        viewModel.startScan()
        val error = viewModel.state.value as EmptyFolderState.Error
        assertEquals("No permission", error.message)
    }

    @Test
    fun `deleteFolders removes successfully deleted folders from results`() = runTest {
        val f1 = folder("a")
        val f2 = folder("b")
        coEvery { repository.findEmptyFolders(any()) } returns listOf(f1, f2)
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
        val f1 = folder("a"); val f2 = folder("b")
        coEvery { repository.findEmptyFolders(any()) } returns listOf(f1, f2)
        coEvery { repository.deleteFolder(any()) } returns true
        viewModel.startScan()
        viewModel.deleteFolders(listOf(f1, f2))
        assertTrue((viewModel.state.value as EmptyFolderState.Results).folders.isEmpty())
    }

    @Test
    fun `deleteFolders does nothing when state is not Results`() = runTest {
        viewModel.deleteFolders(listOf(folder("a")))
        assertTrue(viewModel.state.value is EmptyFolderState.Idle)
        coVerify(exactly = 0) { repository.deleteFolder(any()) }
    }
}
