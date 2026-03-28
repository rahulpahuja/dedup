package com.rp.dedup.core.firebase.crashlytics

import com.google.firebase.crashlytics.FirebaseCrashlytics

class FirebaseCrashlyticsManager(private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()) {
    fun log(message: String) {
        crashlytics.log(message)
    }

    fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }
}
