package com.rp.dedup.core.deepoptimization

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for StorageTreeRepository.
 * buildTree() makes MediaStore queries (API 30+) or file-system calls
 * (API < 30) — both require real Android framework and are covered by
 * instrumented tests. Here we test the FolderNode trie structure directly.
 */
class StorageTreeRepositoryTest {

    private val context = mockk<Context>(relaxed = true).also {
        every { it.contentResolver } returns mockk(relaxed = true)
    }

    // ── FolderNode trie logic ─────────────────────────────────────────────────

    private fun node(name: String, size: Long, vararg children: com.rp.dedup.core.model.FolderNode) =
        com.rp.dedup.core.model.FolderNode(name, name, size, children.toList())

    @Test
    fun `FolderNode sizeBytes accumulates children correctly when built manually`() {
        val child1 = node("Downloads", 1024L)
        val child2 = node("DCIM", 2048L)
        val root = node("Storage", child1.sizeBytes + child2.sizeBytes, child1, child2)

        assertEquals(3072L, root.sizeBytes)
    }

    @Test
    fun `FolderNode isLeaf is true for terminal nodes`() {
        val leaf = node("leaf", 512L)
        assertTrue(leaf.isLeaf)
    }

    @Test
    fun `FolderNode isLeaf is false for nodes with children`() {
        val child = node("child", 256L)
        val parent = node("parent", 256L, child)
        assertFalse(parent.isLeaf)
    }

    @Test
    fun `FolderNode with empty children list is a leaf`() {
        val n = com.rp.dedup.core.model.FolderNode("path", "name", 512L, emptyList())
        assertTrue(n.isLeaf)
    }

    @Test
    fun `FolderNode children list is preserved`() {
        val c1 = node("a", 100L)
        val c2 = node("b", 200L)
        val parent = node("parent", 300L, c1, c2)
        assertEquals(2, parent.children.size)
        assertEquals("a", parent.children[0].name)
        assertEquals("b", parent.children[1].name)
    }

    // ── trie path decomposition ────────────────────────────────────────────────

    @Test
    fun `relative path split produces correct segments`() {
        val path = "Download/Docs/Report"
        val parts = path.split('/').filter { it.isNotEmpty() }
        assertEquals(listOf("Download", "Docs", "Report"), parts)
    }

    @Test
    fun `relative path with trailing slash is trimmed correctly`() {
        val path = "Download/Docs/"
        val parts = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
        assertEquals(listOf("Download", "Docs"), parts)
    }

    @Test
    fun `empty relative path produces no segments`() {
        val path = ""
        val parts = path.split('/').filter { it.isNotEmpty() }
        assertTrue(parts.isEmpty())
    }

    // ── repository can be instantiated ────────────────────────────────────────

    @Test
    fun `StorageTreeRepositoryImpl can be instantiated`() {
        val repo = StorageTreeRepositoryImpl(context)
        assertNotNull(repo)
    }
}
