package com.rp.dedup.core.app

import android.app.Application
import com.facebook.FacebookSdk
import com.google.firebase.FirebaseApp

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
    }

}