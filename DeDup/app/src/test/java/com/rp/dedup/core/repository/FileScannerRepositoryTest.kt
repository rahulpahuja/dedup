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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = com.rp.dedup.util.TestApp::class)
class FileScannerRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: FileScannerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = FileScannerRepository(context)
        mockkObject(ChecksumUtils)
        every { ChecksumUtils.calculateSHA256(any(), any()) } returns "mock-hash"
    }

    @After
    fun tearDown() {
        unmockkObject(ChecksumUtils)
        try {
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            context.contentResolver.delete(collection, null, null)
        } catch (_: Exception) { }
    }

    private fun insertFile(name: String, size: Long) {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
            put(MediaStore.Files.FileColumns.SIZE, size)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.Files.FileColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            put(MediaStore.Files.FileColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
        })
    }

    @Test
    fun `scanFilesByExtension returns matching pdf files`() = runTest {
        insertFile("report.pdf", 1024L)
        val files = repository.scanFilesByExtension(listOf("pdf"), deepScan = false).toList()
        assertEquals(1, files.size)
        assertEquals("report.pdf", files[0].name)
        assertEquals(1024L, files[0].size)
        assertNull(files[0].checksum)
    }

    @Test
    fun `scanFilesByExtension calculates checksum when deepScan is true`() = runTest {
        insertFile("report.pdf", 1024L)
        val files = repository.scanFilesByExtension(listOf("pdf"), deepScan = true).toList()
        assertEquals(1, files.size)
        assertEquals("mock-hash", files.first().checksum)
    }

    @Test
    fun `scanFilesByExtension with unknown extension returns nothing`() = runTest {
        insertFile("report.pdf", 1024L)
        val files = repository.scanFilesByExtension(listOf("txt")).toList()
        assertTrue(files.isEmpty())
    }

    @Test
    fun `scanOldFiles for non-existent folder returns empty`() = runTest {
        val oldFiles = repository.scanOldFiles("/storage/emulated/0/NoSuchFolder/", 0L).toList()
        assertTrue(oldFiles.isEmpty())
    }
}
