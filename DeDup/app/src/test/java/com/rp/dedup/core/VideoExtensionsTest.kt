package com.rp.dedup.core

import org.junit.Assert.*
import org.junit.Test

class VideoExtensionsTest {

    @Test
    fun `list contains common video formats`() {
        assertTrue(VideoExtensions.list.containsAll(listOf("mp4", "mkv", "avi", "mov", "webm")))
    }

    @Test
    fun `list contains exactly 25 extensions`() {
        assertEquals(25, VideoExtensions.list.size)
    }

    @Test
    fun `list does not contain image extensions`() {
        listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic").forEach { ext ->
            assertFalse("Should not contain image ext '$ext'", VideoExtensions.list.contains(ext))
        }
    }

    @Test
    fun `list does not contain audio-only extensions`() {
        listOf("mp3", "aac", "flac", "ogg", "wav", "m4a").forEach { ext ->
            assertFalse("Should not contain audio ext '$ext'", VideoExtensions.list.contains(ext))
        }
    }

    @Test
    fun `list contains professional and rare formats`() {
        assertTrue(VideoExtensions.list.contains("mxf"))
        assertTrue(VideoExtensions.list.contains("nsv"))
        assertTrue(VideoExtensions.list.contains("rmvb"))
        assertTrue(VideoExtensions.list.contains("m2ts"))
    }

    @Test
    fun `all entries are lowercase`() {
        VideoExtensions.list.forEach { ext ->
            assertEquals("'$ext' should be lowercase", ext, ext.lowercase())
        }
    }

    @Test
    fun `list has no duplicates`() {
        assertEquals(VideoExtensions.list.size, VideoExtensions.list.toSet().size)
    }

    @Test
    fun `list does not contain empty string or blank entries`() {
        VideoExtensions.list.forEach { ext ->
            assertTrue("Extension should not be blank", ext.isNotBlank())
        }
    }
}
