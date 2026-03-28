package com.rp.dedup.core.image

import com.rp.dedup.core.data.ScannedImage
import org.junit.Assert.*
import org.junit.Test

class ScannedImageTest {

    @Test
    fun `two instances with same values are equal`() {
        val a = ScannedImage("content://media/123", 12345L, 1024L)
        val b = ScannedImage("content://media/123", 12345L, 1024L)
        assertEquals(a, b)
    }

    @Test
    fun `different uri produces unequal instances`() {
        val a = ScannedImage("content://media/1", 0L, 100L)
        val b = ScannedImage("content://media/2", 0L, 100L)
        assertNotEquals(a, b)
    }

    @Test
    fun `different dHash produces unequal instances`() {
        val a = ScannedImage("content://media/1", 100L, 100L)
        val b = ScannedImage("content://media/1", 999L, 100L)
        assertNotEquals(a, b)
    }

    @Test
    fun `copy preserves all fields when unchanged`() {
        val original = ScannedImage("content://media/1", 99L, 512L)
        val copy = original.copy()
        assertEquals(original, copy)
        assertNotSame(original, copy)
    }

    @Test
    fun `copy creates instance with modified dHash only`() {
        val original = ScannedImage("content://media/1", 99L, 512L)
        val copy = original.copy(dHash = 42L)
        assertEquals("content://media/1", copy.uri)
        assertEquals(42L, copy.dHash)
        assertEquals(512L, copy.sizeInBytes)
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val a = ScannedImage("uri", 1L, 2L)
        val b = ScannedImage("uri", 1L, 2L)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `toString contains all field values`() {
        val img = ScannedImage("content://test", 777L, 2048L)
        val str = img.toString()
        assertTrue(str.contains("content://test"))
        assertTrue(str.contains("777"))
        assertTrue(str.contains("2048"))
    }

    @Test
    fun `sizeInBytes can be zero`() {
        val img = ScannedImage("content://empty", 0L, 0L)
        assertEquals(0L, img.sizeInBytes)
    }

    @Test
    fun `dHash can be negative`() {
        val img = ScannedImage("content://x", Long.MIN_VALUE, 100L)
        assertEquals(Long.MIN_VALUE, img.dHash)
    }
}
