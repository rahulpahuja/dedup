package com.rp.dedup.core.utils

import android.net.Uri
import com.rp.dedup.core.model.ScannedFile
import com.rp.dedup.core.model.ScannedImage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class SelectionLogicTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun image(uri: String, size: Long, date: Long = 0L) = ScannedImage(
        uri          = uri,
        dHash        = 0L,
        sizeInBytes  = size,
        dateModified = date
    )

    private fun file(uriStr: String, size: Long): ScannedFile {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns uriStr
        return ScannedFile(uri = uri, name = uriStr.substringAfterLast('/'), size = size, path = uriStr, extension = "zip")
    }

    // ── selectImagesToDelete ──────────────────────────────────────────────────

    @Test
    fun `KEEP_NEWEST keeps image with highest dateModified`() {
        val groups = listOf(listOf(
            image("a", 100, date = 1000L),
            image("b", 100, date = 3000L),
            image("c", 100, date = 2000L)
        ))
        val toDelete = SelectionLogic.selectImagesToDelete(groups, SelectionLogic.Strategy.KEEP_NEWEST)
        assertFalse("b should be kept", toDelete.contains("b"))
        assertTrue(toDelete.contains("a"))
        assertTrue(toDelete.contains("c"))
    }

    @Test
    fun `KEEP_OLDEST keeps image with lowest dateModified`() {
        val groups = listOf(listOf(
            image("a", 100, date = 1000L),
            image("b", 100, date = 3000L)
        ))
        val toDelete = SelectionLogic.selectImagesToDelete(groups, SelectionLogic.Strategy.KEEP_OLDEST)
        assertFalse("a should be kept", toDelete.contains("a"))
        assertTrue(toDelete.contains("b"))
    }

    @Test
    fun `KEEP_LARGEST keeps image with highest size`() {
        val groups = listOf(listOf(
            image("small", 100),
            image("large", 900),
            image("medium", 500)
        ))
        val toDelete = SelectionLogic.selectImagesToDelete(groups, SelectionLogic.Strategy.KEEP_LARGEST)
        assertFalse("large should be kept", toDelete.contains("large"))
        assertTrue(toDelete.contains("small"))
        assertTrue(toDelete.contains("medium"))
    }

    @Test
    fun `KEEP_SMALLEST keeps image with lowest size`() {
        val groups = listOf(listOf(
            image("small", 100),
            image("large", 900)
        ))
        val toDelete = SelectionLogic.selectImagesToDelete(groups, SelectionLogic.Strategy.KEEP_SMALLEST)
        assertFalse("small should be kept", toDelete.contains("small"))
        assertTrue(toDelete.contains("large"))
    }

    @Test
    fun `single-item groups are skipped`() {
        val groups = listOf(listOf(image("only", 100)))
        val toDelete = SelectionLogic.selectImagesToDelete(groups, SelectionLogic.Strategy.KEEP_LARGEST)
        assertTrue(toDelete.isEmpty())
    }

    @Test
    fun `empty groups list returns empty deletion list`() {
        val toDelete = SelectionLogic.selectImagesToDelete(emptyList(), SelectionLogic.Strategy.KEEP_NEWEST)
        assertTrue(toDelete.isEmpty())
    }

    @Test
    fun `multiple groups each keep one image`() {
        val groups = listOf(
            listOf(image("g1a", 100), image("g1b", 200)),
            listOf(image("g2a", 50),  image("g2b", 300))
        )
        val toDelete = SelectionLogic.selectImagesToDelete(groups, SelectionLogic.Strategy.KEEP_LARGEST)
        assertEquals(2, toDelete.size)
        assertFalse(toDelete.contains("g1b"))
        assertFalse(toDelete.contains("g2b"))
    }

    // ── selectFilesToDelete ───────────────────────────────────────────────────

    @Test
    fun `file KEEP_LARGEST keeps file with highest size`() {
        val groups = listOf(listOf(
            file("file://small.zip", 100),
            file("file://large.zip", 900)
        ))
        val toDelete = SelectionLogic.selectFilesToDelete(groups, SelectionLogic.Strategy.KEEP_LARGEST)
        assertFalse(toDelete.contains("file://large.zip"))
        assertTrue(toDelete.contains("file://small.zip"))
    }

    @Test
    fun `file KEEP_SMALLEST keeps file with lowest size`() {
        val groups = listOf(listOf(
            file("file://small.zip", 100),
            file("file://large.zip", 900)
        ))
        val toDelete = SelectionLogic.selectFilesToDelete(groups, SelectionLogic.Strategy.KEEP_SMALLEST)
        assertFalse(toDelete.contains("file://small.zip"))
        assertTrue(toDelete.contains("file://large.zip"))
    }

    @Test
    fun `file KEEP_NEWEST falls back to keeping first element`() {
        val groups = listOf(listOf(
            file("file://first.zip", 100),
            file("file://second.zip", 200)
        ))
        val toDelete = SelectionLogic.selectFilesToDelete(groups, SelectionLogic.Strategy.KEEP_NEWEST)
        assertFalse(toDelete.contains("file://first.zip"))
        assertTrue(toDelete.contains("file://second.zip"))
    }

    @Test
    fun `file single-item groups are skipped`() {
        val groups = listOf(listOf(file("file://only.zip", 100)))
        assertTrue(SelectionLogic.selectFilesToDelete(groups, SelectionLogic.Strategy.KEEP_LARGEST).isEmpty())
    }
}
