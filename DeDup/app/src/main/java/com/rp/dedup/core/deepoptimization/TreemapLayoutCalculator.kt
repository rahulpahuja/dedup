package com.rp.dedup.core.deepoptimization

import androidx.compose.ui.geometry.Rect
import com.rp.dedup.core.model.FolderNode

object TreemapLayoutCalculator {

    data class TreemapCell(
        val rect: Rect,
        val node: FolderNode,
        val depth: Int,
        val colorIndex: Int
    )

    fun compute(nodes: List<FolderNode>, bounds: Rect, maxDepth: Int = 2): List<TreemapCell> {
        val result = mutableListOf<TreemapCell>()
        val sorted = nodes.filter { it.sizeBytes > 0 }.sortedByDescending { it.sizeBytes }
        layout(sorted, bounds, currentDepth = 0, maxDepth = maxDepth, colorOffset = 0, result = result)
        return result
    }

    private fun layout(
        nodes: List<FolderNode>,
        bounds: Rect,
        currentDepth: Int,
        maxDepth: Int,
        colorOffset: Int,
        result: MutableList<TreemapCell>
    ) {
        if (nodes.isEmpty() || bounds.width < 4f || bounds.height < 4f) return

        val total = nodes.sumOf { it.sizeBytes }.toFloat()
        val splitHorizontally = bounds.width >= bounds.height
        var offset = 0f

        nodes.forEachIndexed { index, node ->
            val fraction = if (total > 0f) node.sizeBytes / total else 1f / nodes.size
            val colorIndex = (colorOffset + index) % COLOR_COUNT

            val rect = if (splitHorizontally) {
                val w = bounds.width * fraction
                Rect(bounds.left + offset, bounds.top, bounds.left + offset + w, bounds.bottom)
                    .also { offset += w }
            } else {
                val h = bounds.height * fraction
                Rect(bounds.left, bounds.top + offset, bounds.right, bounds.top + offset + h)
                    .also { offset += h }
            }

            result.add(TreemapCell(rect, node, currentDepth, colorIndex))

            if (currentDepth < maxDepth && node.children.isNotEmpty()) {
                val padding = if (currentDepth == 0) 20f else 2f
                val inner = Rect(
                    rect.left + padding, rect.top + padding,
                    rect.right - padding, rect.bottom - padding
                )
                val sortedChildren = node.children
                    .filter { it.sizeBytes > 0 }
                    .sortedByDescending { it.sizeBytes }
                layout(sortedChildren, inner, currentDepth + 1, maxDepth, colorOffset + nodes.size, result)
            }
        }
    }

    const val COLOR_COUNT = 8
}
