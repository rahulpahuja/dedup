package com.rp.dedup.core.image

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import androidx.core.graphics.scale

object ImageHasher {

    fun calculateDHash(bitmap: Bitmap): Long {
        // 1. Resize to 9x8 (72 pixels total) using a pooled bitmap if possible
        val resized = BitmapPool.acquire(9, 8, Bitmap.Config.ARGB_8888)
        resized.eraseColor(Color.TRANSPARENT)
        
        // Ensure no density scaling during the draw
        resized.density = bitmap.density
        val canvas = android.graphics.Canvas(resized)
        val rect = android.graphics.Rect(0, 0, 9, 8)
        
        // Use a paint with filtering for better quality downscaling
        val paint = android.graphics.Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(bitmap, null, rect, paint)
        
        var hash = 0L

        // 2 & 3. Compare adjacent pixels
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val leftPixel = resized.getPixel(x, y)
                val rightPixel = resized.getPixel(x + 1, y)

                val leftLuminance = getLuminance(leftPixel)
                val rightLuminance = getLuminance(rightPixel)

                // If left is brighter than right, set the bit to 1
                if (leftLuminance > rightLuminance) {
                    val bitPosition = y * 8 + x
                    hash = hash or (1L shl bitPosition)
                }
            }
        }

        // Return to pool instead of recycling
        BitmapPool.release(resized)

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