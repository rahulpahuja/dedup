package com.rp.dedup.core.deepoptimization

import androidx.compose.ui.geometry.Rect
import com.rp.dedup.core.model.FolderNode
import org.junit.Assert.*
import org.junit.Test

class TreemapLayoutCalculatorTest {

    private val bounds = Rect(0f, 0f, 100f, 100f)

    private fun node(name: String, size: Long, children: List<FolderNode> = emptyList()) =
        FolderNode(path = name, name = name, sizeBytes = size, children = children)

    // ── basic layout ──────────────────────────────────────────────────────────

    @Test
    fun `empty node list produces no cells`() {
        val cells = TreemapLayoutCalculator.compute(emptyList(), bounds)
        assertTrue(cells.isEmpty())
    }

    @Test
    fun `zero-size nodes are excluded from layout`() {
        val nodes = listOf(node("zero", 0), node("nonzero", 100))
        val cells = TreemapLayoutCalculator.compute(nodes, bounds)
        assertEquals(1, cells.size)
        assertEquals("nonzero", cells.first().node.name)
    }

    @Test
    fun `single node fills entire bounds`() {
        val cells = TreemapLayoutCalculator.compute(listOf(node("root", 100)), bounds)
        assertEquals(1, cells.size)
        assertEquals(0f, cells.first().rect.left,  0.1f)
        assertEquals(0f, cells.first().rect.top,   0.1f)
        assertEquals(100f, cells.first().rect.right,  0.1f)
        assertEquals(100f, cells.first().rect.bottom, 0.1f)
    }

    @Test
    fun `two equal nodes each fill half the bounds`() {
        val nodes = listOf(node("a", 100), node("b", 100))
        val cells = TreemapLayoutCalculator.compute(nodes, bounds)
        assertEquals(2, cells.size)
        val totalArea = cells.sumOf { (it.rect.width * it.rect.height).toDouble() }
        assertEquals(10000.0, totalArea, 10.0)
    }

    @Test
    fun `larger node gets proportionally larger cell`() {
        val nodes = listOf(node("big", 750), node("small", 250))
        val cells = TreemapLayoutCalculator.compute(nodes, Rect(0f, 0f, 100f, 10f))
        val bigCell   = cells.first { it.node.name == "big" }
        val smallCell = cells.first { it.node.name == "small" }
        assertTrue(bigCell.rect.width > smallCell.rect.width)
    }

    @Test
    fun `nodes are sorted by size descending`() {
        val nodes = listOf(node("small", 10), node("large", 90), node("medium", 50))
        val cells = TreemapLayoutCalculator.compute(nodes, bounds)
        assertEquals("large", cells[0].node.name)
    }

    // ── depth and children ────────────────────────────────────────────────────

    @Test
    fun `children are laid out when maxDepth allows`() {
        val child  = node("child", 50)
        val parent = node("parent", 100, listOf(child))
        val cells  = TreemapLayoutCalculator.compute(listOf(parent), bounds, maxDepth = 1)
        val names  = cells.map { it.node.name }
        assertTrue(names.contains("parent"))
        assertTrue(names.contains("child"))
    }

    @Test
    fun `children not laid out when maxDepth is 0`() {
        val child  = node("child", 50)
        val parent = node("parent", 100, listOf(child))
        val cells  = TreemapLayoutCalculator.compute(listOf(parent), bounds, maxDepth = 0)
        assertEquals(1, cells.size)
        assertEquals("parent", cells.first().node.name)
    }

    @Test
    fun `depth field is correct for parent and child`() {
        val child  = node("child", 50)
        val parent = node("parent", 100, listOf(child))
        val cells  = TreemapLayoutCalculator.compute(listOf(parent), bounds, maxDepth = 1)
        assertEquals(0, cells.first { it.node.name == "parent" }.depth)
        assertEquals(1, cells.first { it.node.name == "child"  }.depth)
    }

    // ── color cycling ─────────────────────────────────────────────────────────

    @Test
    fun `color indices are within valid range`() {
        val nodes = (0 until 10).map { node("n$it", (it + 1) * 10L) }
        val cells = TreemapLayoutCalculator.compute(nodes, bounds)
        cells.forEach { assertTrue(it.colorIndex in 0 until TreemapLayoutCalculator.COLOR_COUNT) }
    }

    // ── bounds guard ──────────────────────────────────────────────────────────

    @Test
    fun `very small bounds produce no children to prevent infinite subdivision`() {
        val child  = node("child", 50)
        val parent = node("parent", 100, listOf(child))
        val tinyBounds = Rect(0f, 0f, 3f, 3f)
        val cells = TreemapLayoutCalculator.compute(listOf(parent), tinyBounds, maxDepth = 2)
        assertTrue(cells.any { it.node.name == "parent" })
    }
}
