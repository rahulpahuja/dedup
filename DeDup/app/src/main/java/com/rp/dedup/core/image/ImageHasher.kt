package com.rp.dedup.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

object ImageHasher {

    // One Paint and one draw-Rect per thread — avoids allocating these objects
    // for every one of the 1,000+ images processed concurrently on Dispatchers.IO.
    private val threadLocalPaint = ThreadLocal.withInitial {
        Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
    }
    private val threadLocalRect = ThreadLocal.withInitial { Rect(0, 0, 9, 8) }

    fun calculateDHash(bitmap: Bitmap): Long {
        val resized = BitmapPool.acquire(9, 8, Bitmap.Config.ARGB_8888)
        resized.eraseColor(Color.TRANSPARENT)
        resized.density = bitmap.density

        val canvas = Canvas(resized)
        canvas.drawBitmap(bitmap, null, threadLocalRect.get()!!, threadLocalPaint.get())

        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val leftPixel = resized.getPixel(x, y)
                val rightPixel = resized.getPixel(x + 1, y)
                if (getLuminance(leftPixel) > getLuminance(rightPixel)) {
                    hash = hash or (1L shl (y * 8 + x))
                }
            }
        }

        BitmapPool.release(resized)
        return hash
    }

    private fun getLuminance(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    fun calculateHammingDistance(hash1: Long, hash2: Long): Int =
        java.lang.Long.bitCount(hash1 xor hash2)
}
