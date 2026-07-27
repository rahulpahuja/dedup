package com.rp.dedup.core.repository

import com.rp.dedup.core.dao.ScanHistoryDao
import com.rp.dedup.core.model.ScanHistory
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScanHistoryRepositoryTest {

    private val dao = mockk<ScanHistoryDao>(relaxed = true)
    private lateinit var repository: ScanHistoryRepository

    private fun scan(id: Long = 1L, type: String = "IMAGE") = ScanHistory(
        id               = id,
        scanType         = type,
        timestamp        = 1_000_000L,
        durationMs       = 250L,
        totalScanned     = 50,
        duplicateGroups  = 3,
        totalDuplicates  = 5,
        reclaimableBytes = 4096L,
        status           = "COMPLETED"
    )

    @Before
    fun setUp() {
        repository = ScanHistoryRepository(dao)
    }

    // ── getAll ─────────────────────────────────────────────────────────────────

    @Test
    fun `getAll returns flow from DAO`() = runTest {
        val records = listOf(scan(1), scan(2))
        every { dao.getAll() } returns flowOf(records)

        val result = repository.getAll().first()

        assertEquals(2, result.size)
    }

    @Test
    fun `getAll with empty DAO returns empty list`() = runTest {
        every { dao.getAll() } returns flowOf(emptyList())

        val result = repository.getAll().first()

        assertTrue(result.isEmpty())
    }

    // ── insert ─────────────────────────────────────────────────────────────────

    @Test
    fun `insert delegates to DAO`() = runTest {
        val s = scan(10L)
        repository.insert(s)
        coVerify(exactly = 1) { dao.insert(s) }
    }

    // ── delete ─────────────────────────────────────────────────────────────────

    @Test
    fun `delete delegates to DAO`() = runTest {
        val s = scan(5L)
        repository.delete(s)
        coVerify(exactly = 1) { dao.delete(s) }
    }

    // ── clearAll ───────────────────────────────────────────────────────────────

    @Test
    fun `clearAll delegates to DAO`() = runTest {
        repository.clearAll()
        coVerify(exactly = 1) { dao.clearAll() }
    }

    // ── data integrity ─────────────────────────────────────────────────────────

    @Test
    fun `getAll preserves scan type field`() = runTest {
        every { dao.getAll() } returns flowOf(listOf(scan(1, "VIDEO"), scan(2, "IMAGE")))

        val result = repository.getAll().first()

        assertEquals("VIDEO", result[0].scanType)
        assertEquals("IMAGE", result[1].scanType)
    }
}
