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

class CleanupViewModelExtendedTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<FileScannerRepository>(relaxed = true)

    private fun file(name: String, size: Long, ext: String = "mp4") = ScannedFile(
        uri = mockk<Uri>(),
        name = name,
        size = size,
        path = "/storage/$name",
        extension = ext
    )

    private fun setup(
        videos: List<ScannedFile> = emptyList(),
        archives: List<ScannedFile> = emptyList(),
        apks: List<ScannedFile> = emptyList(),
        oldFiles: List<ScannedFile> = emptyList()
    ) {
        every { repository.scanFilesByExtension(listOf("mp4", "mkv", "mov", "avi")) } returns
            flowOf(*videos.toTypedArray())
        every { repository.scanFilesByExtension(listOf("zip", "rar", "7z", "tar", "gz")) } returns
            flowOf(*archives.toTypedArray())
        every { repository.scanFilesByExtension(listOf("apk", "obb")) } returns
            flowOf(*apks.toTypedArray())
        every { repository.scanOldFiles(any(), any()) } returns
            flowOf(*oldFiles.toTypedArray())
    }

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has zero counts for all categories`() = runTest {
        setup()
        val vm = CleanupViewModel(repository)
        val state = vm.uiState.value
        assertEquals(0, state.videoStats.count)
        assertEquals(0, state.archiveStats.count)
        assertEquals(0, state.appDownloadStats.count)
        assertEquals(0, state.oldDownloadStats.count)
    }

    // ── video size threshold boundary ──────────────────────────────────────────

    @Test
    fun `video at exactly 10MB boundary is excluded`() = runTest {
        val exactly10mb = file("exact.mp4", 10 * 1024 * 1024L)
        setup(videos = listOf(exactly10mb))
        val vm = CleanupViewModel(repository)
        assertEquals(0, vm.uiState.value.videoStats.count)
    }

    @Test
    fun `video at 10MB + 1 byte is included`() = runTest {
        val justOver = file("over.mp4", 10 * 1024 * 1024L + 1)
        setup(videos = listOf(justOver))
        val vm = CleanupViewModel(repository)
        assertEquals(1, vm.uiState.value.videoStats.count)
    }

    @Test
    fun `mkv file above threshold is included`() = runTest {
        val mkv = file("movie.mkv", 50 * 1024 * 1024L, "mkv")
        setup(videos = listOf(mkv))
        val vm = CleanupViewModel(repository)
        assertEquals(1, vm.uiState.value.videoStats.count)
    }

    @Test
    fun `mov file above threshold is included`() = runTest {
        val mov = file("clip.mov", 20 * 1024 * 1024L, "mov")
        setup(videos = listOf(mov))
        val vm = CleanupViewModel(repository)
        assertEquals(1, vm.uiState.value.videoStats.count)
    }

    // ── apk size threshold boundary ───────────────────────────────────────────

    @Test
    fun `apk at exactly 1MB boundary is excluded`() = runTest {
        val exactly1mb = file("app.apk", 1 * 1024 * 1024L, "apk")
        setup(apks = listOf(exactly1mb))
        val vm = CleanupViewModel(repository)
        assertEquals(0, vm.uiState.value.appDownloadStats.count)
    }

    @Test
    fun `apk at 1MB + 1 byte is included`() = runTest {
        val justOver = file("app.apk", 1 * 1024 * 1024L + 1, "apk")
        setup(apks = listOf(justOver))
        val vm = CleanupViewModel(repository)
        assertEquals(1, vm.uiState.value.appDownloadStats.count)
    }

    @Test
    fun `obb file above threshold is included`() = runTest {
        val obb = file("data.obb", 100 * 1024 * 1024L, "obb")
        setup(apks = listOf(obb))
        val vm = CleanupViewModel(repository)
        assertEquals(1, vm.uiState.value.appDownloadStats.count)
    }

    // ── archive — no size threshold ───────────────────────────────────────────

    @Test
    fun `tiny archive is still counted`() = runTest {
        val zip = file("tiny.zip", 100L, "zip")
        setup(archives = listOf(zip))
        val vm = CleanupViewModel(repository)
        assertEquals(1, vm.uiState.value.archiveStats.count)
    }

    @Test
    fun `all archive extensions are counted`() = runTest {
        val files = listOf(
            file("a.zip", 100L, "zip"),
            file("b.rar", 200L, "rar"),
            file("c.7z", 300L, "7z"),
            file("d.tar", 400L, "tar"),
            file("e.gz", 500L, "gz")
        )
        setup(archives = files)
        val vm = CleanupViewModel(repository)
        assertEquals(5, vm.uiState.value.archiveStats.count)
        assertEquals(1500L, vm.uiState.value.archiveStats.totalSize)
    }

    // ── totalSize is correct across categories ────────────────────────────────

    @Test
    fun `videoStats totalSize sums only included video files`() = runTest {
        val big1 = file("a.mp4", 20 * 1024 * 1024L)
        val big2 = file("b.mp4", 30 * 1024 * 1024L)
        val small = file("c.mp4", 1 * 1024 * 1024L)
        setup(videos = listOf(big1, big2, small))
        val vm = CleanupViewModel(repository)
        assertEquals(50 * 1024 * 1024L, vm.uiState.value.videoStats.totalSize)
    }

    // ── refreshAll re-scans ───────────────────────────────────────────────────

    @Test
    fun `refreshAll with no files results in zero counts`() = runTest {
        setup()
        val vm = CleanupViewModel(repository)
        vm.refreshAll()
        assertEquals(0, vm.uiState.value.videoStats.count)
        assertEquals(0, vm.uiState.value.archiveStats.count)
    }

    @Test
    fun `refreshAll replaces previous scan results`() = runTest {
        val v1 = file("a.mp4", 20 * 1024 * 1024L)
        setup(videos = listOf(v1))
        val vm = CleanupViewModel(repository)
        assertEquals(1, vm.uiState.value.videoStats.count)

        setup(videos = emptyList())
        vm.refreshAll()
        assertEquals(0, vm.uiState.value.videoStats.count)
    }
}
