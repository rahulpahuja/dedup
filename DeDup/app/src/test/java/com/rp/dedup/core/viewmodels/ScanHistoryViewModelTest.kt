package com.rp.dedup.core.viewmodels

import com.rp.dedup.core.model.ScanHistory
import com.rp.dedup.core.repository.ScanHistoryRepository
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScanHistoryViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository = mockk<ScanHistoryRepository>(relaxed = true)
    private lateinit var viewModel: ScanHistoryViewModel

    private fun scan(id: Long, type: String = "IMAGE") = ScanHistory(
        id               = id,
        scanType         = type,
        timestamp        = System.currentTimeMillis(),
        durationMs       = 100L,
        totalScanned     = 10,
        duplicateGroups  = 2,
        totalDuplicates  = 3,
        reclaimableBytes = 1024L,
        status           = "COMPLETED"
    )

    @Before
    fun setUp() {
        every { repository.getAll() } returns flowOf(emptyList())
        viewModel = ScanHistoryViewModel(repository)
    }

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial history is empty when repository returns empty list`() = runTest {
        assertTrue(viewModel.history.value.isEmpty())
    }

    @Test
    fun `history reflects repository data`() = runTest {
        val records = listOf(scan(1), scan(2), scan(3))
        every { repository.getAll() } returns flowOf(records)
        val vm = ScanHistoryViewModel(repository)
        assertEquals(3, vm.history.value.size)
    }

    // ── delete ─────────────────────────────────────────────────────────────────

    @Test
    fun `delete calls repository delete with correct scan`() = runTest {
        val s = scan(42)
        viewModel.delete(s)
        coVerify { repository.delete(s) }
    }

    // ── clearAll ───────────────────────────────────────────────────────────────

    @Test
    fun `clearAll calls repository clearAll`() = runTest {
        viewModel.clearAll()
        coVerify { repository.clearAll() }
    }
}
