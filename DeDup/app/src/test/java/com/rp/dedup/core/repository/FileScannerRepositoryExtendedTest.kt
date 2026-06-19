package com.rp.dedup.core.repository

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.rp.dedup.core.utils.ChecksumUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric's MediaStore shadow only reliably stores files in the Files table when the
// MIME type is "application/pdf". Other types (image/jpeg, text/plain, application/zip etc.)
// get silently routed to type-specific tables and don't appear in Files queries.
// All inserts therefore use "application/pdf"; tests avoid querying by extensions that
// also have a MIME-type OR clause (only "pdf" and "apk" do in the production code).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = com.rp.dedup.util.TestApp::class)
class FileScannerRepositoryExtendedTest {

    private lateinit var context: Context
    private lateinit var repository: FileScannerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = FileScannerRepository(context)
        mockkObject(ChecksumUtils)
        every { ChecksumUtils.calculateSHA256(any(), any()) } returns "sha256-stub"
    }

    @After
    fun tearDown() {
        unmockkObject(ChecksumUtils)
        try {
            context.contentResolver.delete(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), null, null
            )
        } catch (_: Exception) { }
    }

    private fun insert(name: String, size: Long = 1024L) {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
            put(MediaStore.Files.FileColumns.SIZE, size)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.Files.FileColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            put(MediaStore.Files.FileColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
        })
    }

    // ── extension matching (pdf queries — MIME filter + display name filter) ────

    @Test
    fun `scanFilesByExtension returns pdf files by display name`() = runTest {
        insert("doc.pdf")
        val results = repository.scanFilesByExtension(listOf("pdf")).toList()
        assertFalse("Should find at least one pdf file", results.isEmpty())
        assertEquals("doc.pdf", results[0].name)
    }

    @Test
    fun `scanFilesByExtension with unknown extension returns no files`() = runTest {
        insert("doc.pdf")
        // "xyz" has no MIME-type clause, relies on DISPLAY_NAME LIKE "%.xyz" which doesn't match
        val results = repository.scanFilesByExtension(listOf("xyz")).toList()
        assertTrue("Unknown extension should return empty", results.isEmpty())
    }

    @Test
    fun `scanFilesByExtension returns nothing when MediaStore is empty`() = runTest {
        val results = repository.scanFilesByExtension(listOf("pdf")).toList()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `scanFilesByExtension two matching files both returned`() = runTest {
        insert("a.pdf")
        insert("b.pdf")
        val results = repository.scanFilesByExtension(listOf("pdf")).toList()
        assertEquals(2, results.size)
    }

    @Test
    fun `scanFilesByExtension three matching files all returned`() = runTest {
        insert("x.pdf")
        insert("y.pdf")
        insert("z.pdf")
        val results = repository.scanFilesByExtension(listOf("pdf")).toList()
        assertEquals(3, results.size)
    }

    // ── checksum — deepScan flag ───────────────────────────────────────────────

    @Test
    fun `deepScan false means checksum is null`() = runTest {
        insert("report.pdf")
        val results = repository.scanFilesByExtension(listOf("pdf"), deepScan = false).toList()
        assertFalse("Expected at least one result", results.isEmpty())
        assertNull(results.first().checksum)
    }

    @Test
    fun `deepScan true calls SHA256 and stores checksum`() = runTest {
        insert("report.pdf")
        val results = repository.scanFilesByExtension(listOf("pdf"), deepScan = true).toList()
        assertFalse("Expected at least one result", results.isEmpty())
        assertEquals("sha256-stub", results.first().checksum)
    }

    // ── ScannedFile fields ─────────────────────────────────────────────────────

    @Test
    fun `returned ScannedFile has correct extension from filename`() = runTest {
        insert("archive.pdf")
        val results = repository.scanFilesByExtension(listOf("pdf")).toList()
        assertFalse(results.isEmpty())
        assertEquals("pdf", results.first().extension)
    }

    @Test
    fun `returned ScannedFile has non-null URI`() = runTest {
        insert("report.pdf")
        val results = repository.scanFilesByExtension(listOf("pdf")).toList()
        assertFalse(results.isEmpty())
        assertNotNull(results.first().uri)
    }

    @Test
    fun `returned ScannedFile size field is preserved`() = runTest {
        insert("big.pdf", 999_999L)
        val results = repository.scanFilesByExtension(listOf("pdf")).toList()
        assertFalse(results.isEmpty())
        assertEquals(999_999L, results.first().size)
    }

    @Test
    fun `returned ScannedFile name field is preserved`() = runTest {
        insert("myfile.pdf")
        val results = repository.scanFilesByExtension(listOf("pdf")).toList()
        assertFalse(results.isEmpty())
        assertEquals("myfile.pdf", results.first().name)
    }

    // ── scanOldFiles ───────────────────────────────────────────────────────────

    @Test
    fun `scanOldFiles returns nothing for non-existent folder`() = runTest {
        val results = repository.scanOldFiles("/storage/emulated/0/NoSuchFolder/", 0L).toList()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `scanOldFiles with no files in MediaStore returns empty`() = runTest {
        val results = repository.scanOldFiles("/storage/emulated/0/Download/", System.currentTimeMillis()).toList()
        assertTrue(results.isEmpty())
    }
}
