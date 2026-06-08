package com.rp.dedup.core.deepoptimization

import android.content.Context
import android.net.Uri
import com.rp.dedup.core.model.EmptyFolder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for EmptyFolderRepository pure-logic paths.
 * SAF (DocumentFile) and file-system traversal require real Android
 * framework and are covered by instrumented tests.
 */
class EmptyFolderRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val repository = EmptyFolderRepositoryImpl(context)

    // ── EmptyFolder model ──────────────────────────────────────────────────────

    @Test
    fun `EmptyFolder with no documentUri has null documentUri`() {
        val folder = EmptyFolder("/storage/empty", "empty", "/storage")
        assertNull(folder.documentUri)
    }

    @Test
    fun `EmptyFolder with documentUri stores uri string`() {
        val folder = EmptyFolder("/path", "name", "/", documentUri = "content://saf/tree/42")
        assertEquals("content://saf/tree/42", folder.documentUri)
    }

    @Test
    fun `EmptyFolder stores path, name, and parentPath`() {
        val folder = EmptyFolder("/storage/emulated/0/empty", "empty", "/storage/emulated/0")
        assertEquals("/storage/emulated/0/empty", folder.path)
        assertEquals("empty", folder.name)
        assertEquals("/storage/emulated/0", folder.parentPath)
    }

    // ── Repository creation ────────────────────────────────────────────────────

    @Test
    fun `EmptyFolderRepositoryImpl can be instantiated`() {
        assertNotNull(repository)
    }

    // ── findEmptyFolders — null treeUri on API < 30 ────────────────────────────

    @Test
    fun `findEmptyFolders with null treeUri does not throw on legacy path`() = runTest {
        // On API < 30 the File API path runs; it may return empty list if
        // no real file system is accessible in unit test JVM.
        // Verifying it does not throw is sufficient.
        try {
            repository.findEmptyFolders(null)
        } catch (e: SecurityException) {
            // acceptable — no permission in JVM test environment
        } catch (e: Exception) {
            fail("Unexpected exception: ${e.message}")
        }
    }
}
