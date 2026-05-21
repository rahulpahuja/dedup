package com.rp.dedup.core.utils

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

object ChecksumUtils {
    /**
     * Calculates the SHA-256 checksum of a file.
     * SHA-256 is used instead of MD5 to provide better collision resistance.
     */
    fun calculateSHA256(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
