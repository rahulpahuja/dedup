package com.rp.dedup.core.security

class NativeLib {
    companion object {
        init {
            try {
                System.loadLibrary("dedup-native")
            } catch (e: Exception) {
                // Library not found or already loaded
            }
        }
    }

    external fun getFirebaseDbUrl(): String
    external fun getGoogleWebClientId(): String
    external fun getFacebookAppId(): String
    external fun getFacebookClientToken(): String
}
