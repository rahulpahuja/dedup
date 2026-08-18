package com.rp.dedup.core.security

import com.google.firebase.crashlytics.FirebaseCrashlytics

class NativeLib {
    companion object {
        // Caught here (not left to propagate out of the companion's init) so a load failure
        // on any device doesn't permanently poison the class — a thrown class initializer
        // leaves every later NativeLib() access throwing NoClassDefFoundError instead of the
        // original error, which is what let getGoogleWebClientId() crash Google sign-in
        // unguarded even though the caller only ever caught UnsatisfiedLinkError.
        val isAvailable: Boolean = try {
            System.loadLibrary("dedup_native")
            true
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            false
        }
    }

    external fun getFirebaseDbUrl(): String
    external fun getGoogleWebClientId(): String
    external fun getFacebookAppId(): String
    external fun getFacebookClientToken(): String
}
