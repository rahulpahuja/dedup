package com.rp.dedup.core.fixes

import android.graphics.Bitmap
import com.rp.dedup.core.image.BitmapPool
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix #28 — BitmapPool.acquire() returned a pooled bitmap without checking that its
 * dimensions (width × height) and Bitmap.Config matched what the caller requested.
 * ImageHasher always requests 9×8 ARGB_8888, but a future caller with different
 * dimensions would silently receive a wrong-size bitmap, producing corrupted dHash values.
 *
 * The fix: acquire() now validates width, height, and config. Mismatched pooled bitmaps
 * are recycled and a fresh one is allocated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = com.rp.dedup.util.TestApp::class)
class Fix28BitmapPoolDimensionValidationTest {

    @Test
    fun `acquire returns correct dimensions when pool is empty`() {
        BitmapPool.clear()
        val bmp = BitmapPool.acquire(9, 8, Bitmap.Config.ARGB_8888)
        try {
            assertEquals("Width must match request", 9, bmp.width)
            assertEquals("Height must match request", 8, bmp.height)
            assertEquals("Config must match request", Bitmap.Config.ARGB_8888, bmp.config)
        } finally {
            BitmapPool.release(bmp)
        }
    }

    @Test
    fun `acquire does not return a pooled bitmap with wrong dimensions`() {
        BitmapPool.clear()
        // Put a 9×8 bitmap into the pool
        val wrongSize = Bitmap.createBitmap(9, 8, Bitmap.Config.ARGB_8888)
        BitmapPool.release(wrongSize)

        // Ask for a different size — must NOT get the 9×8 back
        val bmp = BitmapPool.acquire(16, 16, Bitmap.Config.ARGB_8888)
        try {
            assertEquals("Returned bitmap width must match request (16), not pool entry (9)", 16, bmp.width)
            assertEquals("Returned bitmap height must match request (16), not pool entry (8)", 16, bmp.height)
        } finally {
            BitmapPool.release(bmp)
        }
    }

    @Test
    fun `acquire does not return a pooled bitmap with wrong config`() {
        BitmapPool.clear()
        val wrongConfig = Bitmap.createBitmap(9, 8, Bitmap.Config.RGB_565)
        BitmapPool.release(wrongConfig)

        val bmp = BitmapPool.acquire(9, 8, Bitmap.Config.ARGB_8888)
        try {
            assertEquals(
                "Returned bitmap config must match ARGB_8888, not the pooled RGB_565",
                Bitmap.Config.ARGB_8888, bmp.config
            )
        } finally {
            BitmapPool.release(bmp)
        }
    }

    @Test
    fun `acquire returns a matching pooled bitmap when dimensions and config match`() {
        BitmapPool.clear()
        val original = Bitmap.createBitmap(9, 8, Bitmap.Config.ARGB_8888)
        val originalId = System.identityHashCode(original)
        BitmapPool.release(original)

        val acquired = BitmapPool.acquire(9, 8, Bitmap.Config.ARGB_8888)
        try {
            assertEquals(
                "When dimensions and config match, the pooled bitmap must be reused",
                originalId, System.identityHashCode(acquired)
            )
        } finally {
            BitmapPool.release(acquired)
        }
    }

    @Test
    fun `mismatched pooled bitmap is recycled so it does not leak`() {
        BitmapPool.clear()
        val wrongSize = Bitmap.createBitmap(9, 8, Bitmap.Config.ARGB_8888)
        BitmapPool.release(wrongSize)

        // Acquire a different size — wrongSize should be recycled internally
        val bmp = BitmapPool.acquire(16, 16, Bitmap.Config.ARGB_8888)
        BitmapPool.release(bmp)

        assertTrue(
            "The mismatched pooled bitmap must be recycled by acquire() to avoid leaking",
            wrongSize.isRecycled
        )
    }
}
