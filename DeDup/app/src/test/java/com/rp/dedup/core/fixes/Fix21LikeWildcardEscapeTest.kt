package com.rp.dedup.core.fixes

import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #21 — FileScannerRepository.scanOldFiles() built a MediaStore LIKE selection
 * using a raw folder path string without escaping SQL wildcards. A folder path
 * containing '%' or '_' would match unintended files. The DATA column LIKE query
 * is also the only place where user-influenced values entered the selection string.
 *
 * The fix: the folder string is escaped (% → \%, _ → \_, \ → \\) and the query
 * uses "LIKE ? ESCAPE '\\'" so SQLite treats those characters literally.
 *
 * This test validates the escaping logic directly, since the repository cannot be
 * instantiated in a unit-test context (requires Android ContentResolver).
 */
class Fix21LikeWildcardEscapeTest {

    // Mirrors the escaping applied in FileScannerRepository.scanOldFiles()
    private fun escapeForLike(folder: String): String =
        folder.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    @Test
    fun `plain path passes through unchanged`() {
        val path = "/storage/emulated/0/Download"
        assertEquals("/storage/emulated/0/Download", escapeForLike(path))
    }

    @Test
    fun `percent sign is escaped`() {
        val path = "/storage/emulated/0/Down%load"
        assertEquals("/storage/emulated/0/Down\\%load", escapeForLike(path))
    }

    @Test
    fun `underscore is escaped`() {
        val path = "/storage/emulated/0/my_folder"
        assertEquals("/storage/emulated/0/my\\_folder", escapeForLike(path))
    }

    @Test
    fun `backslash is escaped first to avoid double-escaping`() {
        val path = "/storage/emulated/0/back\\slash"
        assertEquals("/storage/emulated/0/back\\\\slash", escapeForLike(path))
    }

    @Test
    fun `combined special characters are all escaped`() {
        val path = "/sdcard/50%_off\\deals"
        assertEquals("/sdcard/50\\%\\_off\\\\deals", escapeForLike(path))
    }

    @Test
    fun `appended wildcard suffix still matches the directory prefix`() {
        val path = "/storage/emulated/0/Download"
        val pattern = "${escapeForLike(path)}%"
        assertTrue(
            "Plain path with appended % must still be a valid prefix pattern",
            pattern.startsWith("/storage/emulated/0/Download")
        )
        assertTrue("Pattern must end with unescaped %", pattern.endsWith("%"))
    }
}
