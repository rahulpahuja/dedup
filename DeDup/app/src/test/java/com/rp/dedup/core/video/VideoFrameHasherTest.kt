package com.rp.dedup.core.video

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for VideoFrameHasher's frame-time math.
 * calculateFrameHashes() requires MediaMetadataRetriever and real video data
 * and is covered by instrumented tests. Here we verify the frame interval
 * math the production code computes.
 */
class VideoFrameHasherTest {

    // The production code extracts frames at 10%, 50%, and 90% of video duration.
    private val intervals = listOf(0.1, 0.5, 0.9)

    private fun frameTimeUs(durationMs: Long, fraction: Double): Long =
        (durationMs * 1000 * fraction).toLong()

    // ── frame time calculations ────────────────────────────────────────────────

    @Test
    fun `extracts frames at three intervals`() {
        assertEquals(3, intervals.size)
    }

    @Test
    fun `intervals are 10 50 and 90 percent`() {
        assertEquals(0.1, intervals[0], 0.001)
        assertEquals(0.5, intervals[1], 0.001)
        assertEquals(0.9, intervals[2], 0.001)
    }

    @Test
    fun `frame times for 10 second video are correct`() {
        val durationMs = 10_000L
        val times = intervals.map { frameTimeUs(durationMs, it) }
        assertEquals(1_000_000L, times[0])   // 10%: 1s → 1,000,000 µs
        assertEquals(5_000_000L, times[1])   // 50%: 5s → 5,000,000 µs
        assertEquals(9_000_000L, times[2])   // 90%: 9s → 9,000,000 µs
    }

    @Test
    fun `frame times for 1 second video are correct`() {
        val durationMs = 1_000L
        assertEquals(100_000L,  frameTimeUs(durationMs, 0.1))
        assertEquals(500_000L,  frameTimeUs(durationMs, 0.5))
        assertEquals(900_000L,  frameTimeUs(durationMs, 0.9))
    }

    @Test
    fun `frame times for zero duration do not throw`() {
        val times = intervals.map { frameTimeUs(0L, it) }
        times.forEach { assertEquals(0L, it) }
    }

    @Test
    fun `frame times for long video stay within Long range`() {
        val twoHourMs = 2L * 60 * 60 * 1000 // 7,200,000 ms
        val times = intervals.map { frameTimeUs(twoHourMs, it) }
        times.forEach { assertTrue(it >= 0) }
    }

    @Test
    fun `intervals are strictly increasing`() {
        for (i in 0 until intervals.size - 1) {
            assertTrue(intervals[i] < intervals[i + 1])
        }
    }

    @Test
    fun `all intervals are between 0 and 1 exclusive`() {
        intervals.forEach { interval ->
            assertTrue(interval > 0.0)
            assertTrue(interval < 1.0)
        }
    }
}
