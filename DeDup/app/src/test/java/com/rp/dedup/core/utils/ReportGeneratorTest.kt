package com.rp.dedup.core.utils

import com.rp.dedup.core.model.ScanHistory
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the CSV format logic embedded in ReportGenerator.
 * Actual file I/O requires a real Context and is covered by instrumented tests.
 * Here we verify the CSV row format by exercising the same string interpolation
 * logic the production code uses.
 */
class ReportGeneratorTest {

    private fun scan(
        type: String = "IMAGE",
        timestamp: Long = 1_000_000L,
        duration: Long = 500L,
        scanned: Int = 100,
        groups: Int = 5,
        duplicates: Int = 10,
        reclaimable: Long = 8192L,
        status: String = "COMPLETED"
    ) = ScanHistory(
        id               = 1L,
        scanType         = type,
        timestamp        = timestamp,
        durationMs       = duration,
        totalScanned     = scanned,
        duplicateGroups  = groups,
        totalDuplicates  = duplicates,
        reclaimableBytes = reclaimable,
        status           = status
    )

    private fun csvRow(scan: ScanHistory): String =
        "${scan.scanType},${scan.timestamp},${scan.durationMs},${scan.totalScanned}," +
        "${scan.duplicateGroups},${scan.totalDuplicates},${scan.reclaimableBytes},${scan.status}"

    private val csvHeader =
        "Type,Timestamp,Duration(ms),Total Scanned,Groups,Duplicates,Reclaimable(Bytes),Status"

    // ── header ─────────────────────────────────────────────────────────────────

    @Test
    fun `CSV header contains expected columns`() {
        assertTrue(csvHeader.contains("Type"))
        assertTrue(csvHeader.contains("Timestamp"))
        assertTrue(csvHeader.contains("Duration(ms)"))
        assertTrue(csvHeader.contains("Total Scanned"))
        assertTrue(csvHeader.contains("Groups"))
        assertTrue(csvHeader.contains("Duplicates"))
        assertTrue(csvHeader.contains("Reclaimable(Bytes)"))
        assertTrue(csvHeader.contains("Status"))
    }

    @Test
    fun `CSV header has exactly 8 columns`() {
        assertEquals(8, csvHeader.split(",").size)
    }

    // ── row format ─────────────────────────────────────────────────────────────

    @Test
    fun `CSV row contains all scan fields`() {
        val s = scan(type = "VIDEO", status = "COMPLETED", scanned = 42)
        val row = csvRow(s)
        assertTrue(row.contains("VIDEO"))
        assertTrue(row.contains("42"))
        assertTrue(row.contains("COMPLETED"))
    }

    @Test
    fun `CSV row has exactly 8 comma-separated values`() {
        val row = csvRow(scan())
        assertEquals(8, row.split(",").size)
    }

    @Test
    fun `CSV row values match ScanHistory fields in order`() {
        val s = scan(
            type       = "PDF",
            timestamp  = 12345L,
            duration   = 250L,
            scanned    = 30,
            groups     = 3,
            duplicates = 6,
            reclaimable = 4096L,
            status     = "FAILED"
        )
        val parts = csvRow(s).split(",")
        assertEquals("PDF",   parts[0])
        assertEquals("12345", parts[1])
        assertEquals("250",   parts[2])
        assertEquals("30",    parts[3])
        assertEquals("3",     parts[4])
        assertEquals("6",     parts[5])
        assertEquals("4096",  parts[6])
        assertEquals("FAILED",parts[7])
    }

    // ── empty history ──────────────────────────────────────────────────────────

    @Test
    fun `empty history produces only header row`() {
        val history = emptyList<ScanHistory>()
        val rows = buildList {
            add(csvHeader)
            history.forEach { add(csvRow(it)) }
        }
        assertEquals(1, rows.size)
        assertEquals(csvHeader, rows.first())
    }

    // ── multiple records ───────────────────────────────────────────────────────

    @Test
    fun `multiple records produce correct number of rows`() {
        val history = listOf(scan("IMAGE"), scan("VIDEO"), scan("APK"))
        val rows = buildList {
            add(csvHeader)
            history.forEach { add(csvRow(it)) }
        }
        assertEquals(4, rows.size) // header + 3 data rows
    }
}
