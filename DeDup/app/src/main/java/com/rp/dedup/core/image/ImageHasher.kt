package com.rp.dedup.core.image

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import androidx.core.graphics.scale

object ImageHasher {

    fun calculateDHash(bitmap: Bitmap): Long {
        // 1. Resize to 9x8 (72 pixels total)
        val resized = bitmap.scale(9, 8)
        var hash = 0L

        // 2 & 3. Compare adjacent pixels
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val leftPixel = resized[x, y]
                val rightPixel = resized[x + 1, y]

                val leftLuminance = getLuminance(leftPixel)
                val rightLuminance = getLuminance(rightPixel)

                // If left is brighter than right, set the bit to 1
                if (leftLuminance > rightLuminance) {
                    val bitPosition = y * 8 + x
                    hash = hash or (1L shl bitPosition)
                }
            }
        }

        // Free up memory immediately
        if (resized != bitmap) {
            resized.recycle()
        }

        return hash
    }

    // Standard formula to convert RGB to perceived brightness (luminance)
    private fun getLuminance(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    // Compares two hashes. Returns the number of differing bits.
    fun calculateHammingDistance(hash1: Long, hash2: Long): Int {
        // XOR the hashes, then count how many 1s are left
        return java.lang.Long.bitCount(hash1 xor hash2)
    }
}