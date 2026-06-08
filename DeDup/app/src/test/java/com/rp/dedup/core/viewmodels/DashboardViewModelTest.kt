package com.rp.dedup.core.viewmodels

import android.content.Context
import com.rp.dedup.core.model.ScanHistory
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DashboardViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val historyRepository = mockk<ScanHistoryRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun scan(reclaimable: Long) = ScanHistory(
        id               = 1L,
        scanType         = "IMAGE",
        timestamp        = 1_000L,
        durationMs       = 100L,
        totalScanned     = 10,
        duplicateGroups  = 1,
        totalDuplicates  = 2,
        reclaimableBytes = reclaimable,
        status           = "COMPLETED"
    )

    @Before
    fun setUp() {
        every { historyRepository.getAll() } returns flowOf(emptyList())
        // Provide a minimal ContentResolver mock to avoid NPE in loadMediaCounts
        val contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
    }

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial storageStats has zero values`() = runTest {
        val vm = DashboardViewModel(historyRepository, context)
        assertEquals(0L, vm.storageStats.value.totalBytes)
        assertEquals(0L, vm.storageStats.value.usedBytes)
        assertEquals(0L, vm.storageStats.value.freeBytes)
    }

    @Test
    fun `initial mediaCounts has zero values`() = runTest {
        val vm = DashboardViewModel(historyRepository, context)
        assertEquals(0, vm.mediaCounts.value.images)
        assertEquals(0, vm.mediaCounts.value.videos)
        assertEquals(0, vm.mediaCounts.value.pdfs)
        assertEquals(0, vm.mediaCounts.value.apks)
    }

    // ── totalReclaimableBytes ──────────────────────────────────────────────────

    @Test
    fun `totalReclaimableBytes sums all scan records`() = runTest {
        every { historyRepository.getAll() } returns flowOf(
            listOf(scan(1024L), scan(2048L), scan(512L))
        )
        val vm = DashboardViewModel(historyRepository, context)
        assertEquals(3584L, vm.totalReclaimableBytes.value)
    }

    @Test
    fun `totalReclaimableBytes is zero with no scan history`() = runTest {
        every { historyRepository.getAll() } returns flowOf(emptyList())
        val vm = DashboardViewModel(historyRepository, context)
        assertEquals(0L, vm.totalReclaimableBytes.value)
    }

    @Test
    fun `totalReclaimableBytes is zero with single zero-reclaimable scan`() = runTest {
        every { historyRepository.getAll() } returns flowOf(listOf(scan(0L)))
        val vm = DashboardViewModel(historyRepository, context)
        assertEquals(0L, vm.totalReclaimableBytes.value)
    }
}
