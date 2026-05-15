package com.rp.dedup.core.app

import android.app.Application
import com.facebook.FacebookSdk
import com.google.firebase.FirebaseApp
import net.sqlcipher.database.SQLiteDatabase

class DeDupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
        } catch (_: Exception) {
            // Fallback for older versions or specific configurations
            SQLiteDatabase.loadLibs(this)
        }
        FirebaseApp.initializeApp(applicationContext)
        FacebookSdk.fullyInitialize()
    }

}