package com.rp.dedup.core.app

import android.app.Application
import android.util.Log
import com.facebook.FacebookSdk
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.rp.dedup.BuildConfig

class DeDupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
        } catch (_: Exception) {
            // Native library already loaded or unavailable
        }
        FirebaseApp.initializeApp(applicationContext)
        FacebookSdk.fullyInitialize()

        // Log Firebase Token for debugging and verify registration (Non-Prod only)
        if (BuildConfig.DEBUG || BuildConfig.FLAVOR == "dev") {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("DeDupApp", "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result
                Log.d("DeDupApp", "Firebase Messaging Token: $token")
            }
        }

        // Subscribe to a general topic for announcements
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
    }
}
