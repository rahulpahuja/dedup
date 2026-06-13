package com.rp.dedup.core.app

import android.app.Application
import android.util.Log
import com.facebook.FacebookSdk
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.rp.dedup.BuildConfig
import com.rp.dedup.core.security.NativeLib
import com.rp.dedup.core.work.ImageIndexWorker

class DeDupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
        } catch (_: UnsatisfiedLinkError) {
            // Expected in unit tests or if library is missing
        } catch (_: Exception) {
            // Native library already loaded or unavailable
        }
        FirebaseApp.initializeApp(applicationContext)
        // Set token programmatically — keeps it out of manifest (which is readable in any APK decompiler)
        FacebookSdk.setClientToken(NativeLib().getFacebookClientToken())
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

        // Kick off background semantic indexing of on-device images.
        // KEEP policy: no-ops if already running or enqueued.
        ImageIndexWorker.enqueue(this)
    }
}
