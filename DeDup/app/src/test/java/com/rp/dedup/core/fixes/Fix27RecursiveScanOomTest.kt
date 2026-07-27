package com.rp.dedup.core.fixes

import com.rp.dedup.core.repository.FileScannerRepository
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #27 — FileScannerRepository.scanDirectoryRecursively() accumulated all matching
 * files into a MutableList<ScannedFile> before returning the entire list to the caller.
 * On devices with MANAGE_EXTERNAL_STORAGE and a large file tree this could allocate
 * hundreds of MB before emitting a single result, risking OOM.
 *
 * The fix: replaced the recursive accumulator with an iterative DFS using an ArrayDeque
 * stack. Each matching file is passed to an `emit` lambda immediately instead of being
 * added to a list. The new function is a suspend function named emitDirectoryRecursively.
 */
class Fix27RecursiveScanOomTest {

    @Test
    fun `scanDirectoryRecursively no longer exists in FileScannerRepository`() {
        val oldMethod = FileScannerRepository::class.java
            .declaredMethods.find { it.name == "scanDirectoryRecursively" }
        assertNull(
            "scanDirectoryRecursively() must be removed — it accumulated all results into memory before emitting",
            oldMethod
        )
    }

    @Test
    fun `emitDirectoryRecursively exists as a private method`() {
        val newMethod = FileScannerRepository::class.java
            .declaredMethods.find { it.name == "emitDirectoryRecursively" }
        assertNotNull(
            "emitDirectoryRecursively() must replace scanDirectoryRecursively() — emits one file at a time",
            newMethod
        )
        assertTrue(
            "emitDirectoryRecursively must be private",
            java.lang.reflect.Modifier.isPrivate(newMethod!!.modifiers)
        )
    }

    @Test
    fun `emitDirectoryRecursively is a suspend function`() {
        val method = FileScannerRepository::class.java
            .declaredMethods.filter { it.name == "emitDirectoryRecursively" }
            .maxByOrNull { it.parameterCount }
        assertNotNull(method)
        val lastParam = method!!.parameterTypes.lastOrNull()?.name ?: ""
        assertTrue(
            "emitDirectoryRecursively must be a suspend function (Continuation as last parameter)",
            lastParam.contains("Continuation")
        )
    }
}
