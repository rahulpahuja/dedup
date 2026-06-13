package com.rp.dedup.core.repository

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.rp.dedup.core.utils.ChecksumUtils
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
@Config(sdk = [33])
class FileScannerRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: FileScannerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = FileScannerRepository(context)
        mockkStatic(ChecksumUtils::class)
        every { ChecksumUtils.calculateSHA256(any(), any()) } returns "mock-hash"
    }

    @After
    fun tearDown() {
        unmockkStatic(ChecksumUtils::class)
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        context.contentResolver.delete(collection, null, null)
    }

    private fun insertFileToMediaStore(
        name: String,
        size: Long,
        path: String,
        mimeType: String,
        dateModifiedSeconds: Long = System.currentTimeMillis() / 1000
    ) {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
            put(MediaStore.Files.FileColumns.SIZE, size)
            put(MediaStore.Files.FileColumns.DATA, path)
            put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
            put(MediaStore.Files.FileColumns.DATE_MODIFIED, dateModifiedSeconds)
            put(MediaStore.Files.FileColumns.DATE_ADDED, dateModifiedSeconds)
        }
        context.contentResolver.insert(collection, values)
    }

    @Test
    fun `scanFilesByExtension returns matching files`() = runTest {
        insertFileToMediaStore("report.pdf", 1024L, "/storage/emulated/0/report.pdf", "application/pdf")
        insertFileToMediaStore("app.apk", 2048L, "/storage/emulated/0/app.apk", "application/vnd.android.package-archive")
        insertFileToMediaStore("photo.jpg", 3072L, "/storage/emulated/0/photo.jpg", "image/jpeg")

        val files = repository.scanFilesByExtension(listOf("pdf", "apk"), deepScan = false).toList()
        assertEquals(2, files.size)

        val pdf = files.first { it.name == "report.pdf" }
        assertEquals(1024L, pdf.size)
        assertEquals("/storage/emulated/0/report.pdf", pdf.path)
        assertEquals("pdf", pdf.extension)
        assertNull(pdf.checksum)

        val apk = files.first { it.name == "app.apk" }
        assertEquals(2048L, apk.size)
        assertEquals("apk", apk.extension)
    }

    @Test
    fun `scanFilesByExtension calculates checksum when deepScan is true`() = runTest {
        insertFileToMediaStore("report.pdf", 1024L, "/storage/emulated/0/report.pdf", "application/pdf")

        val files = repository.scanFilesByExtension(listOf("pdf"), deepScan = true).toList()
        assertEquals(1, files.size)
        assertEquals("mock-hash", files.first().checksum)
    }

    @Test
    fun `scanFilesByExtension respects excludedFolders`() = runTest {
        insertFileToMediaStore("report1.pdf", 100L, "/storage/emulated/0/Download/report1.pdf", "application/pdf")
        insertFileToMediaStore("report2.pdf", 200L, "/storage/emulated/0/Android/data/report2.pdf", "application/pdf")

        val files = repository.scanFilesByExtension(
            extensions = listOf("pdf"),
            excludedFolders = listOf("/storage/emulated/0/Android")
        ).toList()

        assertEquals(1, files.size)
        assertEquals("report1.pdf", files.first().name)
    }

    @Test
    fun `scanOldFiles returns files older than specified duration`() = runTest {
        val nowMs = System.currentTimeMillis()
        val oneDayAgoSec = (nowMs - 24 * 60 * 60 * 1000L) / 1000
        val oneHourAgoSec = (nowMs - 60 * 60 * 1000L) / 1000

        insertFileToMediaStore("old.pdf", 100L, "/storage/emulated/0/Download/old.pdf", "application/pdf", oneDayAgoSec)
        insertFileToMediaStore("new.pdf", 200L, "/storage/emulated/0/Download/new.pdf", "application/pdf", oneHourAgoSec)

        val olderThanMs = nowMs - 12 * 60 * 60 * 1000L
        val oldFiles = repository.scanOldFiles("/storage/emulated/0/Download/", olderThanMs).toList()

        assertEquals(1, oldFiles.size)
        assertEquals("old.pdf", oldFiles.first().name)
    }
}
