package com.rp.dedup.core.viewmodels

import android.net.Uri
import com.rp.dedup.core.model.ScannedFile
import com.rp.dedup.core.repository.FileScannerRepository
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CleanupViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<FileScannerRepository>(relaxed = true)

    private fun uri(s: String): Uri = Uri.parse(s)

    private fun file(name: String, size: Long, ext: String = "mp4") = ScannedFile(
        uri = uri("content://$name"),
        name = name,
        size = size,
        path = "/storage/$name",
        extension = ext
    )

    private fun makeViewModel() = CleanupViewModel(repository)

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial scan runs on construction and produces results state`() = runTest {
        val videoFile = file("big.mp4", 20 * 1024 * 1024L)
        every { repository.scanFilesByExtension(listOf("mp4", "mkv", "mov", "avi")) } returns flowOf(videoFile)
        every { repository.scanFilesByExtension(listOf("zip", "rar", "7z", "tar", "gz")) } returns flowOf()
        every { repository.scanFilesByExtension(listOf("apk", "obb")) } returns flowOf()
        every { repository.scanOldFiles(any(), any()) } returns flowOf()

        val vm = makeViewModel()

        assertEquals(1, vm.uiState.value.videoStats.count)
    }

    // ── video scanning ─────────────────────────────────────────────────────────

    @Test
    fun `videos smaller than 10MB are excluded`() = runTest {
        val small = file("small.mp4", 5 * 1024 * 1024L)
        every { repository.scanFilesByExtension(listOf("mp4", "mkv", "mov", "avi")) } returns flowOf(small)
        every { repository.scanFilesByExtension(any()) } returns flowOf()
        every { repository.scanOldFiles(any(), any()) } returns flowOf()

        val vm = makeViewModel()

        assertEquals(0, vm.uiState.value.videoStats.count)
    }

    @Test
    fun `videos larger than 10MB are included`() = runTest {
        val large = file("large.mp4", 15 * 1024 * 1024L)
        every { repository.scanFilesByExtension(listOf("mp4", "mkv", "mov", "avi")) } returns flowOf(large)
        every { repository.scanFilesByExtension(any()) } returns flowOf()
        every { repository.scanOldFiles(any(), any()) } returns flowOf()

        val vm = makeViewModel()

        assertEquals(1, vm.uiState.value.videoStats.count)
        assertEquals(15 * 1024 * 1024L, vm.uiState.value.videoStats.totalSize)
    }

    // ── archive scanning ───────────────────────────────────────────────────────

    @Test
    fun `all archive files are included regardless of size`() = runTest {
        val zip = file("archive.zip", 1024L, "zip")
        every { repository.scanFilesByExtension(listOf("mp4", "mkv", "mov", "avi")) } returns flowOf()
        every { repository.scanFilesByExtension(listOf("zip", "rar", "7z", "tar", "gz")) } returns flowOf(zip)
        every { repository.scanFilesByExtension(listOf("apk", "obb")) } returns flowOf()
        every { repository.scanOldFiles(any(), any()) } returns flowOf()

        val vm = makeViewModel()

        assertEquals(1, vm.uiState.value.archiveStats.count)
    }

    // ── app download scanning ──────────────────────────────────────────────────

    @Test
    fun `apk smaller than 1MB is excluded`() = runTest {
        val tiny = file("small.apk", 500 * 1024L, "apk")
        every { repository.scanFilesByExtension(listOf("mp4", "mkv", "mov", "avi")) } returns flowOf()
        every { repository.scanFilesByExtension(listOf("zip", "rar", "7z", "tar", "gz")) } returns flowOf()
        every { repository.scanFilesByExtension(listOf("apk", "obb")) } returns flowOf(tiny)
        every { repository.scanOldFiles(any(), any()) } returns flowOf()

        val vm = makeViewModel()

        assertEquals(0, vm.uiState.value.appDownloadStats.count)
    }

    @Test
    fun `apk larger than 1MB is included`() = runTest {
        val big = file("big.apk", 5 * 1024 * 1024L, "apk")
        every { repository.scanFilesByExtension(listOf("mp4", "mkv", "mov", "avi")) } returns flowOf()
        every { repository.scanFilesByExtension(listOf("zip", "rar", "7z", "tar", "gz")) } returns flowOf()
        every { repository.scanFilesByExtension(listOf("apk", "obb")) } returns flowOf(big)
        every { repository.scanOldFiles(any(), any()) } returns flowOf()

        val vm = makeViewModel()

        assertEquals(1, vm.uiState.value.appDownloadStats.count)
    }

    // ── totalSize aggregation ──────────────────────────────────────────────────

    @Test
    fun `totalSize is sum of all included files`() = runTest {
        val f1 = file("a.zip", 1000L, "zip")
        val f2 = file("b.zip", 2000L, "zip")
        every { repository.scanFilesByExtension(listOf("mp4", "mkv", "mov", "avi")) } returns flowOf()
        every { repository.scanFilesByExtension(listOf("zip", "rar", "7z", "tar", "gz")) } returns flowOf(f1, f2)
        every { repository.scanFilesByExtension(listOf("apk", "obb")) } returns flowOf()
        every { repository.scanOldFiles(any(), any()) } returns flowOf()

        val vm = makeViewModel()

        assertEquals(3000L, vm.uiState.value.archiveStats.totalSize)
    }

    // ── refreshAll ─────────────────────────────────────────────────────────────

    @Test
    fun `refreshAll re-scans and updates state`() = runTest {
        every { repository.scanFilesByExtension(any()) } returns flowOf()
        every { repository.scanOldFiles(any(), any()) } returns flowOf()
        val vm = makeViewModel()

        val newZip = file("new.zip", 5000L, "zip")
        every { repository.scanFilesByExtension(listOf("zip", "rar", "7z", "tar", "gz")) } returns flowOf(newZip)

        vm.refreshAll()

        assertEquals(1, vm.uiState.value.archiveStats.count)
    }
}
