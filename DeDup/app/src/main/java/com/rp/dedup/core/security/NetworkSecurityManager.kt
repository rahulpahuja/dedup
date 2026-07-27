package com.rp.dedup.core.security

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import kotlinx.coroutines.tasks.await

/**
 * Handles security-related network operations, including Play Integrity API attestation.
 */
object NetworkSecurityManager {
    private const val TAG = "NetworkSecurityManager"

    // This should be your Google Cloud Project Number
    private const val CLOUD_PROJECT_NUMBER = 0L 

    /**
     * Requests an integrity token from the Play Integrity API.
     * @param context Application context
     * @param nonce A unique, server-side generated nonce to prevent replay attacks
     * @return The integrity token string, or null if an error occurred
     */
    suspend fun getIntegrityToken(context: Context, nonce: String): String? {
        if (CLOUD_PROJECT_NUMBER == 0L) {
            Log.w(TAG, "Cloud Project Number not set. Play Integrity check will fail.")
            return null
        }

        val integrityManager = IntegrityManagerFactory.create(context)
        
        return try {
            val response: IntegrityTokenResponse = integrityManager.requestIntegrityToken(
                IntegrityTokenRequest.builder()
                    .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                    .setNonce(nonce)
                    .build()
            ).await()
            
            response.token()
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting integrity token", e)
            null
        }
    }
    
    // Server-side Play Integrity verification — not yet implemented.
    // Send `token` to your backend and validate with the Play Integrity API.
    // Throws so callers fail explicitly instead of silently trusting unverified tokens.
    fun verifyTokenOnServer(@Suppress("UNUSED_PARAMETER") token: String): Boolean {
        throw UnsupportedOperationException("Play Integrity server-side verification is not implemented")
    }
}
