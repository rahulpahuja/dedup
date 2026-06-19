package com.rp.dedup.core.image

import org.junit.Assert.*
import org.junit.Test

/**
 * Additional edge-case coverage for ImageHasher.calculateHammingDistance.
 * The existing ImageHasherTest covers the basic cases; these tests cover
 * boundary values, symmetry, and properties that matter for deduplication logic.
 */
class ImageHasherExtendedTest {

    // ── symmetry ───────────────────────────────────────────────────────────────

    @Test
    fun `hamming distance is symmetric — a-to-b equals b-to-a`() {
        val a = 0x1BCDEF1234567890L
        val b = 0x1234567890BCDEFL
        assertEquals(
            ImageHasher.calculateHammingDistance(a, b),
            ImageHasher.calculateHammingDistance(b, a)
        )
    }

    @Test
    fun `hamming distance is symmetric for negative Long values`() {
        val a = -1L
        val b = Long.MIN_VALUE
        assertEquals(
            ImageHasher.calculateHammingDistance(a, b),
            ImageHasher.calculateHammingDistance(b, a)
        )
    }

    // ── boundary values ────────────────────────────────────────────────────────

    @Test
    fun `distance between Long MIN_VALUE and zero is 1 (only sign bit differs)`() {
        assertEquals(1, ImageHasher.calculateHammingDistance(Long.MIN_VALUE, 0L))
    }

    @Test
    fun `distance between Long MAX_VALUE and -1 is 1 (only sign bit differs)`() {
        assertEquals(1, ImageHasher.calculateHammingDistance(Long.MAX_VALUE, -1L))
    }

    @Test
    fun `distance between Long MIN_VALUE and Long MAX_VALUE is 64`() {
        // MIN = 1000...0, MAX = 0111...1 — all 64 bits differ
        assertEquals(64, ImageHasher.calculateHammingDistance(Long.MIN_VALUE, Long.MAX_VALUE))
    }

    @Test
    fun `distance with itself is always 0 for negative values`() {
        assertEquals(0, ImageHasher.calculateHammingDistance(-42L, -42L))
    }

    // ── threshold classification ───────────────────────────────────────────────

    @Test
    fun `distance of 6 is classified as within default threshold`() {
        val base   = 0b1111111111111111L
        val sixOff = 0b1111111111000000L  // 6 bits flipped
        assertEquals(6, ImageHasher.calculateHammingDistance(base, sixOff))
    }

    @Test
    fun `distance of 7 exceeds default threshold of 6`() {
        val base    = 0b1111111111111111L
        val sevenOff = 0b1111111110000000L  // 7 bits flipped
        assertEquals(7, ImageHasher.calculateHammingDistance(base, sevenOff))
        assertTrue(ImageHasher.calculateHammingDistance(base, sevenOff) > 6)
    }

    // ── specific known bit patterns ────────────────────────────────────────────

    @Test
    fun `every other bit set produces correct distance from zero`() {
        val alternating = 0x5555555555555555L  // 32 bits set
        assertEquals(32, ImageHasher.calculateHammingDistance(alternating, 0L))
    }

    @Test
    fun `inversion of alternating pattern has 64 total bits set combined`() {
        val a = 0x5555555555555555L  // bits at even positions
        // complement of a: all odd-position bits — represented as signed negative Long
        val b = a.inv()
        assertEquals(64, ImageHasher.calculateHammingDistance(a, b))
    }

    // ── transitivity ──────────────────────────────────────────────────────────

    @Test
    fun `distance satisfies triangle inequality`() {
        val a = 0b0000L
        val b = 0b0001L
        val c = 0b0011L
        val ab = ImageHasher.calculateHammingDistance(a, b)
        val bc = ImageHasher.calculateHammingDistance(b, c)
        val ac = ImageHasher.calculateHammingDistance(a, c)
        assertTrue(ac <= ab + bc)
    }
}
