package com.rp.dedup.core.deepoptimization

import com.rp.dedup.core.model.FolderNode
import com.rp.dedup.core.model.BigFileMapState
import com.rp.dedup.core.viewmodels.BigFileMapViewModel
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

class BigFileMapViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<StorageTreeRepository>()
    private lateinit var viewModel: BigFileMapViewModel

    private fun fakeNode(name: String, size: Long, vararg children: FolderNode) =
        FolderNode(path = "/sdcard/$name", name = name, sizeBytes = size, children = children.toList())

    @Before
    fun setUp() {
        viewModel = BigFileMapViewModel(
            repository = repository,
            ioDispatcher = coroutineRule.testDispatcher
        )
    }

    // --- initial state ---

    @Test
    fun `initial state is Idle`() {
        assertTrue(viewModel.state.value is BigFileMapState.Idle)
    }

    // --- startScan ---

    @Test
    fun `startScan transitions to Results with root node`() = runTest {
        val root = fakeNode("Internal Storage", 2000L, fakeNode("DCIM", 1000L), fakeNode("Downloads", 1000L))
        coEvery { repository.buildTree(any()) } returns root

        viewModel.startScan()

        val result = viewModel.state.value as BigFileMapState.Results
        assertEquals("Internal Storage", result.root.name)
        assertEquals(2000L, result.root.sizeBytes)
        assertEquals(2, result.root.children.size)
    }

    @Test
    fun `startScan passes provided maxDepth to repository`() = runTest {
        val root = fakeNode("root", 100L)
        coEvery { repository.buildTree(2) } returns root

        viewModel.startScan(maxDepth = 2)

        coVerify(exactly = 1) { repository.buildTree(2) }
    }

    @Test
    fun `startScan is idempotent while scanning`() = runTest {
        coEvery { repository.buildTree(any()) } coAnswers {
            delay(Long.MAX_VALUE)
            fakeNode("root", 0L)
        }

        viewModel.startScan()
        viewModel.startScan()

        coVerify(exactly = 1) { repository.buildTree(any()) }
    }

    @Test
    fun `startScan transitions to Error on repository exception`() = runTest {
        coEvery { repository.buildTree(any()) } throws RuntimeException("permission denied")

        viewModel.startScan()

        val error = viewModel.state.value as BigFileMapState.Error
        assertEquals("permission denied", error.message)
    }

    // --- cancelScan ---

    @Test
    fun `cancelScan resets state to Idle`() = runTest {
        coEvery { repository.buildTree(any()) } coAnswers {
            delay(Long.MAX_VALUE)
            fakeNode("root", 0L)
        }

        viewModel.startScan()
        viewModel.cancelScan()

        assertTrue(viewModel.state.value is BigFileMapState.Idle)
    }

    @Test
    fun `after cancel a new scan can start`() = runTest {
        val root = fakeNode("root", 100L)
        coEvery { repository.buildTree(any()) } coAnswers {
            delay(Long.MAX_VALUE)
            root
        } andThenAnswer {
            root
        }

        viewModel.startScan()
        viewModel.cancelScan()

        // Should be able to start a fresh scan
        coEvery { repository.buildTree(any()) } returns root
        viewModel.startScan()

        assertTrue(viewModel.state.value is BigFileMapState.Results)
    }

    // --- TreemapLayoutCalculator unit tests ---

    @Test
    fun `TreemapLayoutCalculator produces cells for all non-zero nodes`() {
        val nodes = listOf(
            fakeNode("a", 500L),
            fakeNode("b", 300L),
            fakeNode("c", 200L)
        )
        val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, 400f, 200f)

        val cells = TreemapLayoutCalculator.compute(nodes, bounds, maxDepth = 0)

        assertEquals(3, cells.size)
    }

    @Test
    fun `TreemapLayoutCalculator excludes zero-size nodes`() {
        val nodes = listOf(fakeNode("a", 500L), fakeNode("b", 0L))
        val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, 200f, 100f)

        val cells = TreemapLayoutCalculator.compute(nodes, bounds, maxDepth = 0)

        assertEquals(1, cells.size)
        assertEquals("a", cells.first().node.name)
    }

    @Test
    fun `TreemapLayoutCalculator cell rects cover entire bounds proportionally`() {
        val nodes = listOf(fakeNode("a", 1L), fakeNode("b", 1L))
        val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, 200f, 100f)

        val cells = TreemapLayoutCalculator.compute(nodes, bounds, maxDepth = 0)

        val totalArea = cells.sumOf { (it.rect.width * it.rect.height).toDouble() }
        val boundsArea = (200f * 100f).toDouble()
        assertEquals(boundsArea, totalArea, 1.0)
    }

    @Test
    fun `TreemapLayoutCalculator recurses into children up to maxDepth`() {
        val child = fakeNode("child", 100L)
        val parent = fakeNode("parent", 100L, child)
        val bounds = androidx.compose.ui.geometry.Rect(0f, 0f, 400f, 200f)

        val cells = TreemapLayoutCalculator.compute(listOf(parent), bounds, maxDepth = 1)

        // parent + child = 2 cells
        assertEquals(2, cells.size)
        assertEquals(0, cells.first { it.node.name == "parent" }.depth)
        assertEquals(1, cells.first { it.node.name == "child" }.depth)
    }
}
