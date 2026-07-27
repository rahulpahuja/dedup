package com.rp.dedup.core.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import androidx.core.content.edit

object KeyManager {
    private const val PREFS_NAME = "secure_prefs"
    private const val DB_KEY_ALIAS = "db_encryption_key"

    /**
     * Retrieves or generates the database encryption key.
     * Throws if the AndroidKeyStore is unavailable or corrupted — callers must
     * surface a meaningful error rather than silently resetting.
     */
    fun getOrCreateDatabaseKey(context: Context): ByteArray {
        return getOrGenerateKey(context)
    }

    /**
     * Explicit, user-triggered reset path only — destroys all encrypted data.
     * Call this only in response to a deliberate "reset app data" user action,
     * never automatically on error.
     */
    fun resetEncryptionKey(context: Context) {
        Log.w("KeyManager", "Explicit encryption key reset requested — all encrypted data will be lost")
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry("_androidx_security_master_key_")
            keyStore.deleteEntry(DB_KEY_ALIAS)
        } catch (e: Exception) {
            Log.e("KeyManager", "Failed to delete entries from AndroidKeyStore during reset", e)
        }
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) { clear() }
            val prefsFile = File(context.filesDir.parent, "shared_prefs/$PREFS_NAME.xml")
            if (prefsFile.exists()) prefsFile.delete()
        } catch (e: Exception) {
            Log.e("KeyManager", "Failed to clear shared preferences during reset", e)
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
            sharedPreferences.edit { putString(DB_KEY_ALIAS, Base64.encodeToString(newKey, Base64.DEFAULT)) }
            newKey
        }
    }
}
