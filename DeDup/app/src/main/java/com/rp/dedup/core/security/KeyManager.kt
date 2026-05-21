package com.rp.dedup.core.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import androidx.core.content.edit

/**
 * Manages the generation and retrieval of secure keys for database encryption.
 * Uses EncryptedSharedPreferences to securely store a randomly generated key.
 */
object KeyManager {
    private const val PREFS_NAME = "secure_prefs"
    private const val DB_KEY_ALIAS = "db_encryption_key"

    /**
     * Retrieves an existing database key or generates a new one if it doesn't exist.
     * The key is stored securely using EncryptedSharedPreferences.
     */
    fun getOrCreateDatabaseKey(context: Context): ByteArray {
        return try {
            getOrGenerateKey(context)
        } catch (e: Exception) {
            Log.e("KeyManager", "Failed to get or create database key, attempting recovery", e)
            
            // Serious failure, likely KeyStore corruption or master key mismatch.
            // Wipe everything and start over.
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry("_androidx_security_master_key_")
            } catch (ex: Exception) {
                Log.e("KeyManager", "Failed to delete master key from KeyStore", ex)
            }
            
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
            
            // Try one last time. If it fails now, we let the exception bubble up.
            getOrGenerateKey(context)
        }
    }

    private fun getOrGenerateKey(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val encodedKey = sharedPreferences.getString(DB_KEY_ALIAS, null)
        return if (encodedKey != null) {
            Base64.decode(encodedKey, Base64.DEFAULT)
        } else {
            val newKey = ByteArray(32)
            SecureRandom().nextBytes(newKey)
            val base64Key = Base64.encodeToString(newKey, Base64.DEFAULT)
            sharedPreferences.edit { putString(DB_KEY_ALIAS, base64Key) }
            newKey
        }
    }
}
