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
        private const val EVENT_SCAN_STARTED            = "scan_started"
        private const val EVENT_SCAN_COMPLETED          = "scan_completed"
        private const val EVENT_SCAN_CANCELLED          = "scan_cancelled"
        private const val EVENT_FILES_DELETED           = "files_deleted"
        private const val EVENT_AUTO_CLEAR_INITIATED    = "auto_clear_initiated"
        private const val EVENT_SMART_CLEANUP_VIEWED    = "smart_cleanup_viewed"
        private const val EVENT_TREEMAP_VIEWED          = "treemap_viewed"
        private const val EVENT_FEEDBACK_SUBMITTED      = "feedback_submitted"
        private const val EVENT_FEATURE_REQUESTED       = "feature_requested"
        private const val EVENT_PRIVACY_POLICY_VIEWED   = "privacy_policy_viewed"
        private const val EVENT_LOGIN                   = "login_success"
        private const val EVENT_LOGOUT                  = "logout"
        private const val EVENT_SETTINGS_CHANGED        = "settings_changed"
        private const val EVENT_TUTORIAL_INTERACTION    = "tutorial_interaction"
        private const val EVENT_DEEP_LINK_OPENED        = "deep_link_opened"
        private const val EVENT_ERROR_ENCOUNTERED       = "error_encountered"
        private const val EVENT_IMAGE_PREVIEWED         = "image_previewed"
        private const val EVENT_SCREEN_VIEW             = "screen_view_custom"
        private const val EVENT_CONTACT_MERGE           = "contact_merge"

        // --- Parameter Names ---
        private const val PARAM_SCAN_TYPE        = "scan_type"       // IMAGE, VIDEO, PDF, APK, JUNK, WHATSAPP, SOCIAL, CONTACT
        private const val PARAM_TOTAL_SCANNED    = "total_scanned"
        private const val PARAM_DUPLICATES_FOUND = "duplicates_found"
        private const val PARAM_RECLAIMABLE_BYTES = "reclaimable_bytes"
        private const val PARAM_DELETED_COUNT    = "deleted_count"
        private const val PARAM_FREED_BYTES      = "freed_bytes"
        private const val PARAM_LOGIN_METHOD     = "login_method"    // GOOGLE, FACEBOOK, GUEST
        private const val PARAM_FEEDBACK_TYPE    = "feedback_type"
        private const val PARAM_SETTING_NAME     = "setting_name"    // THEME, LANGUAGE, THRESHOLD
        private const val PARAM_SETTING_VALUE    = "setting_value"
        private const val PARAM_TUTORIAL_NAME    = "tutorial_name"   // DASHBOARD, LONG_PRESS
        private const val PARAM_TUTORIAL_ACTION  = "tutorial_action" // VIEWED, COMPLETED, SKIPPED
        private const val PARAM_ROUTE            = "route"
        private const val PARAM_ERROR_MESSAGE    = "error_message"
        private const val PARAM_SCREEN_NAME      = "screen_name"
        private const val PARAM_MERGED_COUNT     = "merged_count"
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

    fun logSettingChanged(name: String, value: String) {
        val bundle = Bundle().apply {
            putString(PARAM_SETTING_NAME, name)
            putString(PARAM_SETTING_VALUE, value)
        }
        firebaseAnalytics.logEvent(EVENT_SETTINGS_CHANGED, bundle)
    }

    fun logTutorialInteraction(name: String, action: String) {
        val bundle = Bundle().apply {
            putString(PARAM_TUTORIAL_NAME, name)
            putString(PARAM_TUTORIAL_ACTION, action)
        }
        firebaseAnalytics.logEvent(EVENT_TUTORIAL_INTERACTION, bundle)
    }

    fun logDeepLinkOpened(route: String) {
        val bundle = Bundle().apply {
            putString(PARAM_ROUTE, route)
        }
        firebaseAnalytics.logEvent(EVENT_DEEP_LINK_OPENED, bundle)
    }

    fun logError(scanType: String, message: String) {
        val bundle = Bundle().apply {
            putString(PARAM_SCAN_TYPE, scanType)
            putString(PARAM_ERROR_MESSAGE, message)
        }
        firebaseAnalytics.logEvent(EVENT_ERROR_ENCOUNTERED, bundle)
    }

    fun logScanCancelled(scanType: String) {
        val bundle = Bundle().apply {
            putString(PARAM_SCAN_TYPE, scanType)
        }
        firebaseAnalytics.logEvent(EVENT_SCAN_CANCELLED, bundle)
    }

    fun logAutoClearInitiated(scanType: String, savingsBytes: Long) {
        val bundle = Bundle().apply {
            putString(PARAM_SCAN_TYPE, scanType)
            putLong(PARAM_RECLAIMABLE_BYTES, savingsBytes)
        }
        firebaseAnalytics.logEvent(EVENT_AUTO_CLEAR_INITIATED, bundle)
    }

    fun logImagePreviewed() {
        firebaseAnalytics.logEvent(EVENT_IMAGE_PREVIEWED, null)
    }

    fun logScreenView(screenName: String) {
        val bundle = Bundle().apply {
            putString(PARAM_SCREEN_NAME, screenName)
        }
        firebaseAnalytics.logEvent(EVENT_SCREEN_VIEW, bundle)
    }

    fun logContactMerge(mergedCount: Int) {
        val bundle = Bundle().apply {
            putInt(PARAM_MERGED_COUNT, mergedCount)
        }
        firebaseAnalytics.logEvent(EVENT_CONTACT_MERGE, bundle)
    }
}
