package com.rp.dedup.core.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.rp.dedup.core.image.ImageHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoFrameHasher {
    private const val TAG = "VideoFrameHasher"

    /**
     * Extracts frames at 10%, 50%, and 90% and returns a list of their dHashes.
     */
    suspend fun calculateFrameHashes(context: Context, uri: Uri, durationMs: Long): List<Long> = withContext(Dispatchers.IO) {
        val hashes = mutableListOf<Long>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            
            val intervals = listOf(0.1, 0.5, 0.9)
            for (interval in intervals) {
                val timeUs = (durationMs * 1000 * interval).toLong()
                // OPTION_CLOSEST_SYNC is faster than OPTION_CLOSEST
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    val hash = ImageHasher.calculateDHash(bitmap)
                    hashes.add(hash)
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract frames from $uri", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        hashes
    }
}
