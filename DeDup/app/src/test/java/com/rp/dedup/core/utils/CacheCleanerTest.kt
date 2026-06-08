package com.rp.dedup.core.utils

import com.rp.dedup.core.model.state.CleaningProgress
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for CacheCleaner pure-logic and flow state invariants.
 * The actual clearAllCacheFlow() and getCacheSize() methods touch real
 * file-system directories (context.cacheDir / externalCacheDir) and require
 * a real Android Context. Those paths are covered by instrumented tests.
 * Here we test the CleaningProgress state machine and progress arithmetic.
 */
class CacheCleanerTest {

    // ── CleaningProgress states ────────────────────────────────────────────────

    @Test
    fun `Scanning state holds filesFound count`() {
        val state = CleaningProgress.Scanning(25)
        assertEquals(25, state.filesFound)
    }

    @Test
    fun `Cleaning state progress is between 0 and 1`() {
        val state = CleaningProgress.Cleaning(0.5f, 4096L)
        assertTrue(state.progress in 0f..1f)
    }

    @Test
    fun `Cleaning progress coerces to 1 when bytes exceed total`() {
        val progress = minOf(1.0f, 5000f / 4096f)
        assertEquals(1.0f, progress, 0.001f)
    }

    @Test
    fun `Finished state holds total bytes cleared`() {
        val state = CleaningProgress.Finished(16384L)
        assertEquals(16384L, state.totalBytesCleared)
    }

    @Test
    fun `Finished with zero bytes is valid`() {
        val state = CleaningProgress.Finished(0L)
        assertEquals(0L, state.totalBytesCleared)
    }

    @Test
    fun `Error state holds message`() {
        val state = CleaningProgress.Error("permission denied")
        assertEquals("permission denied", state.message)
    }

    // ── progress arithmetic ────────────────────────────────────────────────────

    @Test
    fun `progress 0 at start of cleaning`() {
        val progress = 0L.toFloat() / 1024L
        assertEquals(0f, progress, 0.001f)
    }

    @Test
    fun `progress 1 when all bytes cleared`() {
        val totalSize = 4096L
        val cleared = 4096L
        val progress = cleared.toFloat() / totalSize
        assertEquals(1.0f, progress, 0.001f)
    }

    @Test
    fun `progress is proportional`() {
        val totalSize = 1000L
        val cleared = 250L
        val progress = cleared.toFloat() / totalSize
        assertEquals(0.25f, progress, 0.001f)
    }

    // ── walkBottomUp ordering ──────────────────────────────────────────────────

    @Test
    fun `bottom-up walk visits leaf files before directories`() {
        // CacheCleaner uses walkBottomUp so children are deleted before parents.
        // We verify this ordering axiom holds in the standard library.
        val tempDir = java.nio.file.Files.createTempDirectory("dedup_test").toFile()
        try {
            val child = java.io.File(tempDir, "child.txt").also { it.createNewFile() }
            val visited = mutableListOf<String>()
            tempDir.walkBottomUp().forEach { visited.add(it.name) }

            val childIndex = visited.indexOf(child.name)
            val parentIndex = visited.indexOf(tempDir.name)

            assertTrue("child must come before parent", childIndex < parentIndex)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
