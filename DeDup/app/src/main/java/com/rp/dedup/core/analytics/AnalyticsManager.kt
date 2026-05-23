package com.rp.dedup.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Central manager for tracking critical events in Firebase Analytics.
 */
class AnalyticsManager(context: Context) {

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    companion object {
        // --- Event Names ---
        private const val EVENT_SCAN_STARTED = "scan_started"
        private const val EVENT_SCAN_COMPLETED = "scan_completed"
        private const val EVENT_FILES_DELETED = "files_deleted"
        private const val EVENT_SMART_CLEANUP_VIEWED = "smart_cleanup_viewed"
        private const val EVENT_TREEMAP_VIEWED = "treemap_viewed"
        private const val EVENT_FEEDBACK_SUBMITTED = "feedback_submitted"
        private const val EVENT_FEATURE_REQUESTED = "feature_requested"
        private const val EVENT_PRIVACY_POLICY_VIEWED = "privacy_policy_viewed"
        private const val EVENT_LOGIN = "login_success"
        private const val EVENT_LOGOUT = "logout"

        // --- Parameter Names ---
        private const val PARAM_SCAN_TYPE = "scan_type" // IMAGE, VIDEO, PDF, APK, JUNK
        private const val PARAM_TOTAL_SCANNED = "total_scanned"
        private const val PARAM_DUPLICATES_FOUND = "duplicates_found"
        private const val PARAM_RECLAIMABLE_BYTES = "reclaimable_bytes"
        private const val PARAM_DELETED_COUNT = "deleted_count"
        private const val PARAM_FREED_BYTES = "freed_bytes"
        private const val PARAM_LOGIN_METHOD = "login_method" // GOOGLE, FACEBOOK
        private const val PARAM_FEEDBACK_TYPE = "feedback_type"
    }

    fun logScanStarted(scanType: String) {
        val bundle = Bundle().apply {
            putString(PARAM_SCAN_TYPE, scanType)
        }
        firebaseAnalytics.logEvent(EVENT_SCAN_STARTED, bundle)
    }

    fun logScanCompleted(scanType: String, totalScanned: Int, duplicatesFound: Int, reclaimableBytes: Long) {
        val bundle = Bundle().apply {
            putString(PARAM_SCAN_TYPE, scanType)
            putInt(PARAM_TOTAL_SCANNED, totalScanned)
            putInt(PARAM_DUPLICATES_FOUND, duplicatesFound)
            putLong(PARAM_RECLAIMABLE_BYTES, reclaimableBytes)
        }
        firebaseAnalytics.logEvent(EVENT_SCAN_COMPLETED, bundle)
    }

    fun logFilesDeleted(scanType: String, count: Int, freedBytes: Long) {
        val bundle = Bundle().apply {
            putString(PARAM_SCAN_TYPE, scanType)
            putInt(PARAM_DELETED_COUNT, count)
            putLong(PARAM_FREED_BYTES, freedBytes)
        }
        firebaseAnalytics.logEvent(EVENT_FILES_DELETED, bundle)
    }

    fun logSmartCleanupViewed() {
        firebaseAnalytics.logEvent(EVENT_SMART_CLEANUP_VIEWED, null)
    }

    fun logTreemapViewed() {
        firebaseAnalytics.logEvent(EVENT_TREEMAP_VIEWED, null)
    }

    fun logFeedbackSubmitted(type: String) {
        val bundle = Bundle().apply {
            putString(PARAM_FEEDBACK_TYPE, type)
        }
        firebaseAnalytics.logEvent(EVENT_FEEDBACK_SUBMITTED, bundle)
    }

    fun logFeatureRequested() {
        firebaseAnalytics.logEvent(EVENT_FEATURE_REQUESTED, null)
    }

    fun logPrivacyPolicyViewed() {
        firebaseAnalytics.logEvent(EVENT_PRIVACY_POLICY_VIEWED, null)
    }

    fun logLogin(method: String) {
        val bundle = Bundle().apply {
            putString(PARAM_LOGIN_METHOD, method)
        }
        firebaseAnalytics.logEvent(EVENT_LOGIN, bundle)
    }

    fun logLogout() {
        firebaseAnalytics.logEvent(EVENT_LOGOUT, null)
    }
}
