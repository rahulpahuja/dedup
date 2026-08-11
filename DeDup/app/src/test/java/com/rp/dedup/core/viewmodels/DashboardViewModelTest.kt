package com.rp.dedup.core.viewmodels

import android.content.Context
import com.rp.dedup.core.model.ForecastConfidence
import com.rp.dedup.core.model.ScanHistory
import com.rp.dedup.core.model.StorageForecast
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.core.repository.StorageForecastingRepository
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

class DashboardViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val historyRepository = mockk<ScanHistoryRepository>(relaxed = true)
    private val forecastingRepository = mockk<StorageForecastingRepository>(relaxed = true)
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
        every { forecastingRepository.forecast } returns flowOf(null)
        // Provide a minimal ContentResolver mock to avoid NPE in loadMediaCounts
        val contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
    }

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial storageStats has zero values`() = runTest {
        val vm = DashboardViewModel(historyRepository, forecastingRepository, context)
        assertEquals(0L, vm.storageStats.value.totalBytes)
        assertEquals(0L, vm.storageStats.value.usedBytes)
        assertEquals(0L, vm.storageStats.value.freeBytes)
    }

    @Test
    fun `initial mediaCounts has zero values`() = runTest {
        val vm = DashboardViewModel(historyRepository, forecastingRepository, context)
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
        val vm = DashboardViewModel(historyRepository, forecastingRepository, context)
        val collectJob = launch { vm.totalReclaimableBytes.collect {} }
        kotlinx.coroutines.yield()
        assertEquals(3584L, vm.totalReclaimableBytes.value)
        collectJob.cancel()
    }

    @Test
    fun `totalReclaimableBytes is zero with no scan history`() = runTest {
        every { historyRepository.getAll() } returns flowOf(emptyList())
        val vm = DashboardViewModel(historyRepository, forecastingRepository, context)
        val collectJob = launch { vm.totalReclaimableBytes.collect {} }
        kotlinx.coroutines.yield()
        assertEquals(0L, vm.totalReclaimableBytes.value)
        collectJob.cancel()
    }

    @Test
    fun `totalReclaimableBytes is zero with single zero-reclaimable scan`() = runTest {
        every { historyRepository.getAll() } returns flowOf(listOf(scan(0L)))
        val vm = DashboardViewModel(historyRepository, forecastingRepository, context)
        val collectJob = launch { vm.totalReclaimableBytes.collect {} }
        kotlinx.coroutines.yield()
        assertEquals(0L, vm.totalReclaimableBytes.value)
        collectJob.cancel()
    }

    // ── storageForecast ──────────────────────────────────────────────────────────
    // Verifies the same forecast the notification worker (ScanWorker.isLowStorage,
    // fed by this same StorageForecastingRepository/DAO) would act on is exactly what
    // the dashboard card renders — no separate/stale copy of the forecast.

    @Test
    fun `initial storageForecast is null before the flow emits`() = runTest {
        val vm = DashboardViewModel(historyRepository, forecastingRepository, context)
        assertNull(vm.storageForecast.value)
    }

    @Test
    fun `storageForecast reflects the repository's forecast flow`() = runTest {
        val forecast = StorageForecast(
            daysRemaining = 3,
            estimatedFullDate = Date(),
            dailyUsageVelocity = 1024L,
            confidence = ForecastConfidence.HIGH
        )
        every { forecastingRepository.forecast } returns flowOf(forecast)

        val vm = DashboardViewModel(historyRepository, forecastingRepository, context)
        val collectJob = launch { vm.storageForecast.collect {} }
        kotlinx.coroutines.yield()

        assertEquals(forecast, vm.storageForecast.value)
        collectJob.cancel()
    }

    @Test
    fun `storageForecast is null when the repository has no forecast yet`() = runTest {
        every { forecastingRepository.forecast } returns flowOf(null)

        val vm = DashboardViewModel(historyRepository, forecastingRepository, context)
        val collectJob = launch { vm.storageForecast.collect {} }
        kotlinx.coroutines.yield()

        assertNull(vm.storageForecast.value)
        collectJob.cancel()
    }
}
