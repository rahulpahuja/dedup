package com.rp.dedup.core.app

import android.app.Application
import com.facebook.FacebookSdk
import com.google.firebase.FirebaseApp

class DeDupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(applicationContext)
        FacebookSdk.fullyInitialize()
    }

}