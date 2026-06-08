package com.rp.dedup.core.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ImageScannerRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val uri = mockk<Uri>()

    init {
        every { context.contentResolver } returns contentResolver
    }

    // ── computePartialCrc32 ────────────────────────────────────────────────────

    @Test
    fun `computePartialCrc32 returns non-negative value for valid content`() {
        val data = ByteArray(1024) { it.toByte() }
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(data)

        val crc = ImageScannerRepository.computePartialCrc32(context, uri)

        assertTrue(crc >= 0)
    }

    @Test
    fun `computePartialCrc32 returns -1 when stream is null`() {
        every { contentResolver.openInputStream(uri) } returns null

        val crc = ImageScannerRepository.computePartialCrc32(context, uri)

        assertEquals(-1L, crc)
    }

    @Test
    fun `computePartialCrc32 is deterministic for same content`() {
        val data = "identical content".toByteArray()
        every { contentResolver.openInputStream(uri) } returnsMany listOf(
            ByteArrayInputStream(data),
            ByteArrayInputStream(data)
        )

        val first  = ImageScannerRepository.computePartialCrc32(context, uri)
        val second = ImageScannerRepository.computePartialCrc32(context, uri)

        assertEquals(first, second)
    }

    @Test
    fun `computePartialCrc32 differs for different content`() {
        val uri2 = mockk<Uri>()
        every { contentResolver.openInputStream(uri) }  returns ByteArrayInputStream("aaa".toByteArray())
        every { contentResolver.openInputStream(uri2) } returns ByteArrayInputStream("bbb".toByteArray())

        val crc1 = ImageScannerRepository.computePartialCrc32(context, uri)
        val crc2 = ImageScannerRepository.computePartialCrc32(context, uri2)

        assertNotEquals(crc1, crc2)
    }

    @Test
    fun `computePartialCrc32 returns -1 when openInputStream throws`() {
        every { contentResolver.openInputStream(uri) } throws java.io.IOException("no file")

        val crc = ImageScannerRepository.computePartialCrc32(context, uri)

        assertEquals(-1L, crc)
    }

    // ── calculateInSampleSize (via reflection) ─────────────────────────────────
    // The method is package-private-equivalent (private companion fun), tested via reflection.

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
        // 200×800: min = 200. reqSize=128. 200/2=100 < 128 → sample stays 1
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
