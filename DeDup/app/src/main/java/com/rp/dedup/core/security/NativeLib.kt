package com.rp.dedup.core.security

import com.google.firebase.crashlytics.FirebaseCrashlytics

class NativeLib {
    companion object {
        init {
            try {
                System.loadLibrary("dedup-native")
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    external fun getFirebaseDbUrl(): String
    external fun getGoogleWebClientId(): String
    external fun getFacebookAppId(): String
    external fun getFacebookClientToken(): String
}
