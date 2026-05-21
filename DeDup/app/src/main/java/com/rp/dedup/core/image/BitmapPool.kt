package com.rp.dedup.core.image

import android.graphics.Bitmap
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A simple Bitmap pool to reduce GC overhead during batch image processing.
 * Currently specialized for the dHash algorithm's 9x8 size.
 */
object BitmapPool {
    private val pool = ConcurrentLinkedQueue<Bitmap>()
    private const val MAX_POOL_SIZE = 20

    @Synchronized
    fun acquire(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val bitmap = iterator.next()
            if (bitmap.width == width && bitmap.height == height && bitmap.config == config) {
                iterator.remove()
                return bitmap
            }
        }
        return Bitmap.createBitmap(width, height, config)
    }

    @Synchronized
    fun release(bitmap: Bitmap) {
        if (pool.size < MAX_POOL_SIZE) {
            pool.offer(bitmap)
        } else {
            bitmap.recycle()
        }
    }
}
