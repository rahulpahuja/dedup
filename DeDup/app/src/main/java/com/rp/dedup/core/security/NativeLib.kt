package com.rp.dedup.core.security

class NativeLib {
    companion object {
        init {
            System.loadLibrary("dedup-native")
        }
    }

    external fun getGoogleWebClientId(): String
    external fun getFacebookAppId(): String
    external fun getFacebookClientToken(): String
    external fun getFirebaseDbUrl(): String
}
