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
