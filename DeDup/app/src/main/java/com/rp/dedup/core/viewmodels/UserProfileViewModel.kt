package com.rp.dedup.core.viewmodels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

class UserProfileViewModel(app: Application) : AndroidViewModel(app) {
    
    private val prefs: SharedPreferences by lazy {
        val context = getApplication<Application>()
        try {
            createPrefs(context)
        } catch (e: Exception) {
            Log.e("UserProfileViewModel", "Failed to create encrypted shared preferences, attempting recovery", e)
            
            // Delete the shared preferences file
            context.getSharedPreferences("user_profile_secure", Context.MODE_PRIVATE).edit { clear() }
            
            // Delete the master key if we suspect it's corrupted
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                keyStore.deleteEntry("_androidx_security_master_key_")
            } catch (ex: Exception) {
                Log.e("UserProfileViewModel", "Failed to delete master key", ex)
            }
            
            createPrefs(context)
        }
    }

    private fun createPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "user_profile_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var name by mutableStateOf(prefs.getString("profile_name", "User") ?: "User")
        private set
    var email by mutableStateOf(prefs.getString("profile_email", "") ?: "")
        private set
    var profileImageUrl by mutableStateOf(prefs.getString("profile_image_url", "") ?: "")
        private set

    fun update(newName: String, newEmail: String, newImageUrl: String? = null) {
        name = newName.trim().ifEmpty { "User" }
        email = newEmail.trim()
        if (newImageUrl != null) {
            profileImageUrl = newImageUrl
        }
        
        prefs.edit().apply {
            putString("profile_name", name)
            putString("profile_email", email)
            if (newImageUrl != null) {
                putString("profile_image_url", profileImageUrl)
            }
        }.apply()
    }
}