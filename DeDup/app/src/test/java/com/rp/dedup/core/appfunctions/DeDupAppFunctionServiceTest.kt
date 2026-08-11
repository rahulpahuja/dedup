package com.rp.dedup.core.appfunctions

import android.net.Uri
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.rp.dedup.feature.voicestorage.data.model.MediaType
import com.rp.dedup.feature.voicestorage.data.model.StorageItem
import com.rp.dedup.feature.voicestorage.domain.SortBy
import com.rp.dedup.feature.voicestorage.domain.SortOrder
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-based: constructing AppFunctionInvalidArgumentException touches
 * android.os.Bundle.EMPTY internally, which is null under a plain JVM unit test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = com.rp.dedup.util.TestApp::class)
class DeDupAppFunctionServiceTest {

    // ── largeFilesFilterConfig ────────────────────────────────────────────────

    @Test
    fun `largeFilesFilterConfig maps zero to no minimum`() {
        val filters = BaseDeDupAppFunctionService.largeFilesFilterConfig(0L)

        assertNull(filters.minSizeBytes)
    }

    @Test
    fun `largeFilesFilterConfig maps a positive value through`() {
        val filters = BaseDeDupAppFunctionService.largeFilesFilterConfig(1024L)

        assertEquals(1024L, filters.minSizeBytes)
    }

    @Test
    fun `largeFilesFilterConfig always sorts by size descending`() {
        val filters = BaseDeDupAppFunctionService.largeFilesFilterConfig(0L)

        assertEquals(SortBy.SIZE, filters.sortBy)
        assertEquals(SortOrder.DESC, filters.sortOrder)
    }

    @Test
    fun `largeFilesFilterConfig covers image, video, and audio`() {
        val filters = BaseDeDupAppFunctionService.largeFilesFilterConfig(0L)

        assertEquals(setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO), filters.mediaTypes)
    }

    // ── validateFindLargeFilesParams ──────────────────────────────────────────

    @Test
    fun `validateFindLargeFilesParams rejects negative minSizeBytes`() {
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            BaseDeDupAppFunctionService.validateFindLargeFilesParams(-1L, 20)
        }
    }

    @Test
    fun `validateFindLargeFilesParams rejects non-positive maxResults`() {
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            BaseDeDupAppFunctionService.validateFindLargeFilesParams(0L, 0)
        }
    }

    @Test
    fun `validateFindLargeFilesParams accepts valid input`() {
        BaseDeDupAppFunctionService.validateFindLargeFilesParams(0L, 20)
    }

    // ── oldPhotosFilterConfig ─────────────────────────────────────────────────

    @Test
    fun `oldPhotosFilterConfig computes dateAddedBefore from olderThanDays`() {
        val now = 1_000_000_000_000L
        val filters = BaseDeDupAppFunctionService.oldPhotosFilterConfig(olderThanDays = 30, nowMs = now)

        assertEquals(now - 30L * 24 * 60 * 60 * 1000, filters.dateAddedBefore)
    }

    @Test
    fun `oldPhotosFilterConfig only queries images, oldest first`() {
        val filters = BaseDeDupAppFunctionService.oldPhotosFilterConfig(olderThanDays = 30, nowMs = 0L)

        assertEquals(setOf(MediaType.IMAGE), filters.mediaTypes)
        assertEquals(SortBy.DATE_ADDED, filters.sortBy)
        assertEquals(SortOrder.ASC, filters.sortOrder)
    }

    // ── validateFindOldPhotosParams ───────────────────────────────────────────

    @Test
    fun `validateFindOldPhotosParams rejects non-positive olderThanDays`() {
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            BaseDeDupAppFunctionService.validateFindOldPhotosParams(0, 20)
        }
    }

    @Test
    fun `validateFindOldPhotosParams rejects non-positive maxResults`() {
        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            BaseDeDupAppFunctionService.validateFindOldPhotosParams(30, 0)
        }
    }

    @Test
    fun `validateFindOldPhotosParams accepts valid input`() {
        BaseDeDupAppFunctionService.validateFindOldPhotosParams(30, 20)
    }

    // ── storageSummaryFilterConfig / summarize ────────────────────────────────

    @Test
    fun `storageSummaryFilterConfig covers image, video, and audio`() {
        val filters = BaseDeDupAppFunctionService.storageSummaryFilterConfig()

        assertEquals(setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO), filters.mediaTypes)
    }

    @Test
    fun `summarize aggregates count and size per media type`() {
        val uri = mockk<Uri>(relaxed = true)
        val items = listOf(
            StorageItem(uri, "a.jpg", 100L, 0L, "image/jpeg", MediaType.IMAGE),
            StorageItem(uri, "b.jpg", 200L, 0L, "image/jpeg", MediaType.IMAGE),
            StorageItem(uri, "c.mp4", 500L, 0L, "video/mp4", MediaType.VIDEO),
        )

        val summary = BaseDeDupAppFunctionService.summarize(items).associateBy { it.mediaType }

        assertEquals(2, summary.getValue("IMAGE").fileCount)
        assertEquals(300L, summary.getValue("IMAGE").totalSizeBytes)
        assertEquals(1, summary.getValue("VIDEO").fileCount)
        assertEquals(500L, summary.getValue("VIDEO").totalSizeBytes)
    }

    @Test
    fun `summarize returns empty list for no items`() {
        assertEquals(emptyList<MediaTypeSummary>(), BaseDeDupAppFunctionService.summarize(emptyList()))
    }

    // ── toStorageFileResult ───────────────────────────────────────────────────

    @Test
    fun `toStorageFileResult maps fields through`() {
        val uri = mockk<Uri>(relaxed = true)
        val item = StorageItem(
            uri = uri,
            displayName = "IMG_2026.jpg",
            sizeInBytes = 12345L,
            dateAdded = 1_000L,
            mimeType = "image/jpeg",
            mediaType = MediaType.IMAGE,
        )

        val result = BaseDeDupAppFunctionService.toStorageFileResult(item)

        assertEquals("IMG_2026.jpg", result.displayName)
        assertEquals(12345L, result.sizeInBytes)
        assertEquals(uri, result.uri)
    }
}
