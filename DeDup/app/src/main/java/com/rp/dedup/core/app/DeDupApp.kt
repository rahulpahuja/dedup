package com.rp.dedup.core.app

import android.app.Application
import com.facebook.FacebookSdk
import com.google.firebase.FirebaseApp
import net.sqlcipher.database.SQLiteDatabase

class DeDupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SQLiteDatabase.loadLibs(this)
        FirebaseApp.initializeApp(applicationContext)
        FacebookSdk.fullyInitialize()
    }

}