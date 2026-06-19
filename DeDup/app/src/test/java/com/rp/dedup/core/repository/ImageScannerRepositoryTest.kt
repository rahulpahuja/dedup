package com.rp.dedup.core.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.rp.dedup.core.image.ImageHasher
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = com.rp.dedup.util.TestApp::class)
class ImageScannerRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: ImageScannerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        repository = ImageScannerRepository(context)

        // Mock companion object and other statics
        mockkObject(ImageScannerRepository.Companion)
        mockkObject(ImageHasher)

        // Default companion mocks
        every { ImageScannerRepository.loadBitmapEfficiently(any(), any(), any()) } returns mockk<Bitmap>(relaxed = true)
        every { ImageScannerRepository.computePartialCrc32(any(), any()) } returns 9999L
        every { ImageHasher.calculateDHash(any()) } returns 12345L
    }

    @After
    fun tearDown() {
        unmockkObject(ImageScannerRepository.Companion)
        unmockkObject(ImageHasher)
        try {
            context.contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null)
        } catch (_: SecurityException) { }
    }

    private fun insertImageToMediaStore(
        name: String,
        size: Long,
        path: String,
        dateModifiedSeconds: Long = System.currentTimeMillis() / 1000
    ) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.SIZE, size)
            put(MediaStore.Images.Media.DATA, path)
            put(MediaStore.Images.Media.DATE_MODIFIED, dateModifiedSeconds)
            put(MediaStore.Images.Media.DATE_ADDED, dateModifiedSeconds)
        }
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    @Test
    fun `scanImagesInParallel returns scanned images`() = runTest {
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        val cursor = android.database.MatrixCursor(columns)
        cursor.addRow(arrayOf<Any>(1L, 1024L, "/storage/emulated/0/pic1.jpg", 1000L))
        cursor.addRow(arrayOf<Any>(2L, 2048L, "/storage/emulated/0/pic2.png", 2000L))

        val mockResolver = mockk<android.content.ContentResolver>()
        val mockContext = mockk<Context>()
        every { mockContext.contentResolver } returns mockResolver
        every {
            mockResolver.query(
                eq(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                any(), any(), any(), any()
            )
        } returns cursor

        val localRepository = ImageScannerRepository(mockContext)
        val images = localRepository.scanImagesInParallel(concurrencyLevel = 2).toList()
        assertEquals(2, images.size)

        val first = images.first { it.uri.contains("pic1.jpg") || it.uri.contains("1") }
        assertEquals(1024L, first.sizeInBytes)
        assertEquals(12345L, first.dHash)
        assertEquals(9999L, first.exactHash)
    }

    @Test
    fun `scanImagesInParallel respects excludedFolders`() = runTest {
        val columns = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        val cursor = android.database.MatrixCursor(columns)
        cursor.addRow(arrayOf<Any>(1L, 1024L, "/storage/emulated/0/Download/pic1.jpg", 1000L))
        cursor.addRow(arrayOf<Any>(2L, 2048L, "/storage/emulated/0/Android/data/pic2.png", 2000L))

        val mockResolver = mockk<android.content.ContentResolver>()
        val mockContext = mockk<Context>()
        every { mockContext.contentResolver } returns mockResolver
        every {
            mockResolver.query(
                eq(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
                any(), any(), any(), any()
            )
        } returns cursor

        val localRepository = ImageScannerRepository(mockContext)
        val images = localRepository.scanImagesInParallel(
            concurrencyLevel = 2,
            excludedFolders = listOf("/storage/emulated/0/Android")
        ).toList()

        assertEquals(1, images.size)
        assertTrue(images.first().uri.contains("pic1.jpg") || images.first().uri.contains("1"))
    }

    // ── computePartialCrc32 ────────────────────────────────────────────────────

    @Test
    fun `computePartialCrc32 returns non-negative value for valid content`() {
        unmockkObject(ImageScannerRepository.Companion)

        val mockContext = mockk<Context>()
        val mockResolver = mockk<android.content.ContentResolver>()
        val mockUri = mockk<Uri>()
        every { mockContext.contentResolver } returns mockResolver

        val data = ByteArray(1024) { it.toByte() }
        every { mockResolver.openInputStream(mockUri) } returns ByteArrayInputStream(data)

        val crc = ImageScannerRepository.computePartialCrc32(mockContext, mockUri)
        assertTrue(crc >= 0)
    }

    @Test
    fun `computePartialCrc32 returns -1 when stream is null`() {
        unmockkObject(ImageScannerRepository.Companion)

        val mockContext = mockk<Context>()
        val mockResolver = mockk<android.content.ContentResolver>()
        val mockUri = mockk<Uri>()
        every { mockContext.contentResolver } returns mockResolver
        every { mockResolver.openInputStream(mockUri) } returns null

        val crc = ImageScannerRepository.computePartialCrc32(mockContext, mockUri)
        assertEquals(-1L, crc)
    }

    @Test
    fun `computePartialCrc32 is deterministic for same content`() {
        unmockkObject(ImageScannerRepository.Companion)

        val mockContext = mockk<Context>()
        val mockResolver = mockk<android.content.ContentResolver>()
        val mockUri = mockk<Uri>()
        every { mockContext.contentResolver } returns mockResolver

        val data = "identical content".toByteArray()
        every { mockResolver.openInputStream(mockUri) } returnsMany listOf(
            ByteArrayInputStream(data),
            ByteArrayInputStream(data)
        )

        val first  = ImageScannerRepository.computePartialCrc32(mockContext, mockUri)
        val second = ImageScannerRepository.computePartialCrc32(mockContext, mockUri)
        assertEquals(first, second)
    }

    // ── calculateInSampleSize (via reflection) ─────────────────────────────────

    private fun inSampleSize(srcW: Int, srcH: Int, reqSize: Int): Int {
        val m = ImageScannerRepository.Companion::class.java.getDeclaredMethod(
            "calculateInSampleSize", Int::class.java, Int::class.java, Int::class.java
        )
        m.isAccessible = true
        return m.invoke(ImageScannerRepository.Companion, srcW, srcH, reqSize) as Int
    }

    @Test
    fun `calculateInSampleSize returns 1 when source is smaller than target`() {
        assertEquals(1, inSampleSize(64, 64, 128))
    }

    @Test
    fun `calculateInSampleSize returns 1 when source equals target`() {
        assertEquals(1, inSampleSize(128, 128, 128))
    }

    @Test
    fun `calculateInSampleSize returns 2 for 256-pixel source with 128-pixel target`() {
        assertEquals(2, inSampleSize(256, 256, 128))
    }

    @Test
    fun `calculateInSampleSize returns 4 for 512-pixel source with 128-pixel target`() {
        assertEquals(4, inSampleSize(512, 512, 128))
    }

    @Test
    fun `calculateInSampleSize uses min dimension`() {
        assertEquals(1, inSampleSize(200, 800, 128))
    }

    @Test
    fun `calculateInSampleSize result is always power of 2`() {
        listOf(
            Triple(1000, 1000, 128),
            Triple(4000, 3000, 128),
            Triple(2048, 2048, 500)
        ).forEach { (w, h, req) ->
            val s = inSampleSize(w, h, req)
            assertEquals("$s should be power of 2", 0, s and (s - 1))
        }
    }
}
