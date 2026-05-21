package com.rp.dedup.core.security

import com.rp.dedup.BuildConfig

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

    /**
     * Returns the Google Web Client ID from BuildConfig.
     * Previously stored in JNI, now moved to BuildConfig (and local.properties) for better management.
     */
    fun getGoogleWebClientId(): String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    fun getFacebookAppId(): String = BuildConfig.FACEBOOK_APP_ID

    fun getFacebookClientToken(): String = BuildConfig.FACEBOOK_CLIENT_TOKEN

    /**
     * Firebase DB URL is still retrieved via JNI as an example of native obfuscation,
     * though it should ideally be in BuildConfig as well.
     */
    external fun getFirebaseDbUrl(): String
}
