package com.rp.dedup.core.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = com.rp.dedup.util.TestApp::class)
class MemoriesRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: MemoriesRepository

    private fun now(): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.AUGUST, 12, 12, 0, 0)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = MemoriesRepository(context)
    }

    @After
    fun tearDown() {
        try {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            context.contentResolver.delete(collection, null, null)
        } catch (_: Exception) { }
    }

    private fun insertImage(year: Int, month: Int, day: Int): Uri {
        val takenAt = Calendar.getInstance().apply { set(year, month, day, 9, 0, 0) }.timeInMillis
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return context.contentResolver.insert(collection, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$year$month$day.jpg")
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
        }) ?: error("insert failed")
    }

    @Test
    fun `groups photos taken on the same month and day in an earlier year`() = runTest {
        val oneYearAgo = insertImage(2025, Calendar.AUGUST, 12)
        val threeYearsAgo = insertImage(2023, Calendar.AUGUST, 12)

        val groups = repository.findMemories(listOf(oneYearAgo, threeYearsAgo), now = now())

        assertEquals(2, groups.size)
        assertEquals(listOf(oneYearAgo), groups.first { it.yearsAgo == 1 }.uris)
        assertEquals(listOf(threeYearsAgo), groups.first { it.yearsAgo == 3 }.uris)
    }

    @Test
    fun `groups multiple photos from the same past year together`() = runTest {
        val a = insertImage(2024, Calendar.AUGUST, 12)
        val b = insertImage(2024, Calendar.AUGUST, 12)

        val groups = repository.findMemories(listOf(a, b), now = now())

        assertEquals(1, groups.size)
        assertEquals(2, groups.first().uris.size)
        assertTrue(groups.first().uris.containsAll(listOf(a, b)))
    }

    @Test
    fun `excludes photos taken on a different day`() = runTest {
        val differentDay = insertImage(2025, Calendar.AUGUST, 11)

        val groups = repository.findMemories(listOf(differentDay), now = now())

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `excludes photos taken this year (not an earlier year)`() = runTest {
        val thisYear = insertImage(2026, Calendar.AUGUST, 12)

        val groups = repository.findMemories(listOf(thisYear), now = now())

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `skips uris with no readable DATE_TAKEN instead of crashing`() = runTest {
        val missingDate = Uri.parse("content://media/external/images/media/999999")

        val groups = repository.findMemories(listOf(missingDate), now = now())

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `empty input returns empty output`() = runTest {
        val groups = repository.findMemories(emptyList(), now = now())

        assertTrue(groups.isEmpty())
    }
}
