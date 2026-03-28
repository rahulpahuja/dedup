package com.rp.dedup.core.viewmodels

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class UserProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("user_profile", Context.MODE_PRIVATE)

    var name by mutableStateOf(prefs.getString("profile_name", "User") ?: "User")
        private set
    var email by mutableStateOf(prefs.getString("profile_email", "") ?: "")
        private set

    fun update(newName: String, newEmail: String) {
        name = newName.trim().ifEmpty { "User" }
        email = newEmail.trim()
        prefs.edit()
            .putString("profile_name", name)
            .putString("profile_email", email)
            .apply()
    }
}