package com.rp.dedup.core.image

import org.junit.Assert.*
import org.junit.Test

class ImageHasherTest {

    // --- calculateHammingDistance ---

    @Test
    fun `identical hashes have zero hamming distance`() {
        val hash = 0xDEADBEEFL
        assertEquals(0, ImageHasher.calculateHammingDistance(hash, hash))
    }

    @Test
    fun `both zero hashes have zero distance`() {
        assertEquals(0, ImageHasher.calculateHammingDistance(0L, 0L))
    }

    @Test
    fun `single bit difference returns distance of 1`() {
        assertEquals(1, ImageHasher.calculateHammingDistance(0b0L, 0b1L))
    }

    @Test
    fun `two bit difference returns distance of 2`() {
        assertEquals(2, ImageHasher.calculateHammingDistance(0b000L, 0b101L))
    }

    @Test
    fun `fully opposite hashes have max distance of 64`() {
        assertEquals(64, ImageHasher.calculateHammingDistance(0L, -1L))
    }

//    @Test
//    fun `hamming distance is symmetric`() {
//        val a = 0xABCDEF1234567890L
//        val b = 0x1234567890ABCDEFL
//        assertEquals(
//            ImageHasher.calculateHammingDistance(a, b),
//            ImageHasher.calculateHammingDistance(b, a)
//        )
//    }

    @Test
    fun `distance within threshold 5 classifies as similar`() {
        val base     = 0b1111111100000000L
        val similar  = 0b1111111100000111L // 3 bits differ
        assertTrue(ImageHasher.calculateHammingDistance(base, similar) <= 5)
    }

    @Test
    fun `distance above threshold 5 classifies as different`() {
        val base      = 0b1111111100000000L
        val different = 0b0000000011111111L // 16 bits differ
        assertTrue(ImageHasher.calculateHammingDistance(base, different) > 5)
    }

    @Test
    fun `hash with all bits set has distance 64 from zero`() {
        // -1L = 0xFFFFFFFFFFFFFFFF = all 64 bits set
        assertEquals(64, ImageHasher.calculateHammingDistance(-1L, 0L))
    }

    @Test
    fun `known values produce expected bit count`() {
        // 0b1010 XOR 0b1100 = 0b0110 → 2 bits set
        assertEquals(2, ImageHasher.calculateHammingDistance(0b1010L, 0b1100L))
    }
}
