package com.rp.dedup.core.image

import android.graphics.Bitmap
import java.util.concurrent.ArrayBlockingQueue

/**
 * Thread-safe bitmap pool for the dHash 9×8 scratch bitmaps.
 *
 * Previous implementation used @Synchronized on an object method on top of a
 * ConcurrentLinkedQueue, which created two layers of locking and serialised all
 * four concurrent hashing coroutines. ArrayBlockingQueue is lock-free for
 * non-blocking poll/offer, so callers never contend.
 */
object BitmapPool {
    // 2× the scan concurrency level so threads rarely need to allocate fresh bitmaps.
    private const val POOL_SIZE = 8
    private val pool = ArrayBlockingQueue<Bitmap>(POOL_SIZE)

    fun acquire(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val pooled = pool.poll()
        if (pooled != null && !pooled.isRecycled) return pooled
        return Bitmap.createBitmap(width, height, config)
    }

    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        if (!pool.offer(bitmap)) bitmap.recycle()
    }

    fun clear() {
        var bmp = pool.poll()
        while (bmp != null) {
            bmp.recycle()
            bmp = pool.poll()
        }
    }
}
