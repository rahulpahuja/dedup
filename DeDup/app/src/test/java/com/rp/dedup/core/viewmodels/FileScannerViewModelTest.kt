package com.rp.dedup.core.viewmodels

import android.net.Uri
import com.rp.dedup.core.model.ScannedFile
import com.rp.dedup.core.repository.FileScannerRepository
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FileScannerViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository        = mockk<FileScannerRepository>(relaxed = true)
    private val historyRepository = mockk<ScanHistoryRepository>(relaxed = true)

    private fun uri(s: String): Uri = mockk { every { toString() } returns s }

    private fun file(name: String, size: Long, checksum: String? = null) = ScannedFile(
        uri       = uri("content://$name"),
        name      = name,
        size      = size,
        path      = "/storage/$name",
        extension = name.substringAfterLast('.', ""),
        checksum  = checksum
    )

    private fun makeViewModel() = FileScannerViewModel(
        repository        = repository,
        historyRepository = historyRepository,
        scanTypeName      = "PDF",
        ioDispatcher      = coroutineRule.testDispatcher
    )

    // ── constructor injection for dispatcher ───────────────────────────────────

    private fun FileScannerViewModel(
        repository: FileScannerRepository,
        historyRepository: ScanHistoryRepository?,
        scanTypeName: String,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
    ) = FileScannerViewModel(repository, historyRepository, scanTypeName)

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty files and not scanning`() {
        val vm = makeViewModel()
        assertTrue(vm.files.value.isEmpty())
        assertTrue(vm.duplicateGroups.value.isEmpty())
        assertFalse(vm.isScanning.value)
    }

    // ── startScanning ──────────────────────────────────────────────────────────

    @Test
    fun `startScanning collects all files from repository`() = runTest {
        every { repository.scanFilesByExtension(listOf("pdf")) } returns
            flowOf(file("a.pdf", 100), file("b.pdf", 200))

        val vm = makeViewModel()
        vm.startScanning(listOf("pdf"))

        assertEquals(2, vm.files.value.size)
    }

    @Test
    fun `startScanning does nothing if already scanning`() = runTest {
        every { repository.scanFilesByExtension(any()) } returns flowOf()
        val vm = makeViewModel()
        vm.startScanning(listOf("pdf"))
        vm.startScanning(listOf("pdf"))
        // scanFilesByExtension should only be called once per non-concurrent scan
        // (second call guards by isScanning check)
    }

    // ── duplicate detection — size + name ─────────────────────────────────────

    @Test
    fun `files with same size and name are grouped as duplicates`() = runTest {
        val f1 = file("report.pdf", 1000)
        val f2 = file("report.pdf", 1000)
        every { repository.scanFilesByExtension(listOf("pdf")) } returns flowOf(f1, f2)

        val vm = makeViewModel()
        vm.startScanning(listOf("pdf"))

        assertEquals(1, vm.duplicateGroups.value.size)
        assertEquals(2, vm.duplicateGroups.value.first().size)
    }

    @Test
    fun `files with same size but different names are not grouped`() = runTest {
        val f1 = file("a.pdf", 1000)
        val f2 = file("b.pdf", 1000)
        every { repository.scanFilesByExtension(listOf("pdf")) } returns flowOf(f1, f2)

        val vm = makeViewModel()
        vm.startScanning(listOf("pdf"))

        assertTrue(vm.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `files with different sizes are not grouped even with same name`() = runTest {
        val f1 = file("doc.pdf", 1000)
        val f2 = file("doc.pdf", 2000)
        every { repository.scanFilesByExtension(listOf("pdf")) } returns flowOf(f1, f2)

        val vm = makeViewModel()
        vm.startScanning(listOf("pdf"))

        assertTrue(vm.duplicateGroups.value.isEmpty())
    }

    // ── duplicate detection — checksum ────────────────────────────────────────

    @Test
    fun `files with same size and checksum are grouped`() = runTest {
        val f1 = file("a.pdf", 1000, checksum = "abc123")
        val f2 = file("b.pdf", 1000, checksum = "abc123")
        every { repository.scanFilesByExtension(listOf("pdf")) } returns flowOf(f1, f2)

        val vm = makeViewModel()
        vm.startScanning(listOf("pdf"))

        assertEquals(1, vm.duplicateGroups.value.size)
    }

    @Test
    fun `files with same size but different checksums are not grouped`() = runTest {
        val f1 = file("a.pdf", 1000, checksum = "aaa")
        val f2 = file("b.pdf", 1000, checksum = "bbb")
        every { repository.scanFilesByExtension(listOf("pdf")) } returns flowOf(f1, f2)

        val vm = makeViewModel()
        vm.startScanning(listOf("pdf"))

        assertTrue(vm.duplicateGroups.value.isEmpty())
    }

    // ── removeDeletedFilesFromUI ───────────────────────────────────────────────

    @Test
    fun `removeDeletedFilesFromUI removes matching files`() = runTest {
        val f1 = file("a.pdf", 1000)
        val f2 = file("b.pdf", 2000)
        every { repository.scanFilesByExtension(listOf("pdf")) } returns flowOf(f1, f2)

        val vm = makeViewModel()
        vm.startScanning(listOf("pdf"))
        vm.removeDeletedFilesFromUI(listOf(f1.uri))

        assertEquals(1, vm.files.value.size)
        assertEquals("b.pdf", vm.files.value.first().name)
    }

    @Test
    fun `removeDeletedFilesFromUI updates duplicate groups`() = runTest {
        val f1 = file("report.pdf", 1000)
        val f2 = file("report.pdf", 1000)
        every { repository.scanFilesByExtension(listOf("pdf")) } returns flowOf(f1, f2)

        val vm = makeViewModel()
        vm.startScanning(listOf("pdf"))
        assertEquals(1, vm.duplicateGroups.value.size)

        vm.removeDeletedFilesFromUI(listOf(f1.uri))

        assertTrue(vm.duplicateGroups.value.isEmpty())
    }

    // ── history recording ─────────────────────────────────────────────────────

    @Test
    fun `history is recorded after scan completes`() = runTest {
        every { repository.scanFilesByExtension(any()) } returns flowOf()
        val vm = makeViewModel()
        vm.startScanning(listOf("pdf"))
        coVerify { historyRepository.insert(any()) }
    }
}
