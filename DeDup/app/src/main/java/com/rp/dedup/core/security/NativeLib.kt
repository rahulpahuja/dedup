package com.rp.dedup.core.security

class NativeLib {
    companion object {
        init {
            System.loadLibrary("dedup_native")
        }
    }

    external fun getFirebaseDbUrl(): String
    external fun getGoogleWebClientId(): String
    external fun getFacebookAppId(): String
    external fun getFacebookClientToken(): String
}
