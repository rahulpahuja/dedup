package com.rp.dedup.core.repository

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.rp.dedup.core.video.VideoFrameHasher
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = com.rp.dedup.util.TestApp::class)
class VideoScannerRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: VideoScannerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .grantPermissions(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        repository = VideoScannerRepository(context)
        mockkObject(VideoFrameHasher)
        coEvery { VideoFrameHasher.calculateFrameHashes(any(), any(), any()) } returns listOf(1L, 2L)
    }

    @After
    fun tearDown() {
        unmockkObject(VideoFrameHasher)
        try {
            context.contentResolver.delete(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null)
        } catch (_: SecurityException) { }
    }

    private fun insertVideoToMediaStore(
        name: String,
        size: Long,
        durationMs: Long,
        path: String,
        mimeType: String
    ) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.SIZE, size)
            put(MediaStore.Video.Media.DURATION, durationMs)
            put(MediaStore.Video.Media.DATA, path)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }
        context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }

    @Test
    fun `scanVideos returns only valid videos from MediaStore`() = runTest {
        val columns = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA
        )
        val cursor = android.database.MatrixCursor(columns)
        cursor.addRow(arrayOf<Any>(1L, "movie.mp4", 50000L, 10000L, "video/mp4", "/storage/emulated/0/movie.mp4"))
        cursor.addRow(arrayOf<Any>(2L, "clip.mkv", 60000L, 5000L, "video/x-matroska", "/storage/emulated/0/clip.mkv"))
        cursor.addRow(arrayOf<Any>(3L, "song.mp3", 4000L, 3000L, "audio/mpeg", "/storage/emulated/0/song.mp3"))

        val mockResolver = mockk<android.content.ContentResolver>()
        val mockContext = mockk<Context>()
        every { mockContext.contentResolver } returns mockResolver
        every {
            mockResolver.query(
                eq(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
                any(), any(), any(), any()
            )
        } returns cursor

        val localRepository = VideoScannerRepository(mockContext)
        val videos = localRepository.scanVideos(deepScan = false).toList()
        assertEquals(2, videos.size)

        val mp4 = videos.first { it.name == "movie.mp4" }
        assertEquals(50000L, mp4.sizeInBytes)
        assertEquals(10000L, mp4.durationMs)
        assertEquals("/storage/emulated/0/movie.mp4", mp4.path)
        assertTrue(mp4.frameHashes.isEmpty())

        val mkv = videos.first { it.name == "clip.mkv" }
        assertEquals(60000L, mkv.sizeInBytes)
        assertEquals(5000L, mkv.durationMs)
    }

    @Test
    fun `scanVideos calculates frame hashes when deepScan is true`() = runTest {
        val columns = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA
        )
        val cursor = android.database.MatrixCursor(columns)
        cursor.addRow(arrayOf<Any>(1L, "movie.mp4", 50000L, 10000L, "video/mp4", "/storage/emulated/0/movie.mp4"))

        val mockResolver = mockk<android.content.ContentResolver>()
        val mockContext = mockk<Context>()
        every { mockContext.contentResolver } returns mockResolver
        every {
            mockResolver.query(
                eq(MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
                any(), any(), any(), any()
            )
        } returns cursor

        val localRepository = VideoScannerRepository(mockContext)
        val videos = localRepository.scanVideos(deepScan = true).toList()
        assertEquals(1, videos.size)

        val video = videos.first()
        assertEquals(listOf(1L, 2L), video.frameHashes)
    }
}
