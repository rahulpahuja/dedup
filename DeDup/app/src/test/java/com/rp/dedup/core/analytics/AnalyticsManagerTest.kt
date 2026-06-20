package com.rp.dedup.core.analytics

import android.app.Application
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.analytics.FirebaseAnalytics
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = com.rp.dedup.util.TestApp::class)
class AnalyticsManagerTest {

    private lateinit var mockFirebase: FirebaseAnalytics
    private lateinit var manager: AnalyticsManager

    private val capturedEvent = slot<String>()
    private val capturedBundle = slot<Bundle?>()

    @Before
    fun setUp() {
        mockFirebase = mockk(relaxed = true)
        mockkStatic(FirebaseAnalytics::class)
        every { FirebaseAnalytics.getInstance(any()) } returns mockFirebase
        every { mockFirebase.logEvent(capture(capturedEvent), captureNullable(capturedBundle)) } just Runs

        val app = ApplicationProvider.getApplicationContext<Application>()
        manager = AnalyticsManager(app)
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseAnalytics::class)
    }

    // ── logScanStarted ────────────────────────────────────────────────────────

    @Test
    fun `logScanStarted logs scan_started event`() {
        manager.logScanStarted("IMAGE")
        assertEquals("scan_started", capturedEvent.captured)
    }

    @Test
    fun `logScanStarted includes scan_type parameter`() {
        manager.logScanStarted("VIDEO")
        assertEquals("VIDEO", capturedBundle.captured?.getString("scan_type"))
    }

    // ── logScanCompleted ──────────────────────────────────────────────────────

    @Test
    fun `logScanCompleted logs scan_completed event`() {
        manager.logScanCompleted("PDF", 100, 10, 512_000L)
        assertEquals("scan_completed", capturedEvent.captured)
    }

    @Test
    fun `logScanCompleted includes all parameters`() {
        manager.logScanCompleted("IMAGE", 200, 25, 1_048_576L)
        val bundle = capturedBundle.captured!!
        assertEquals("IMAGE", bundle.getString("scan_type"))
        assertEquals(200, bundle.getInt("total_scanned"))
        assertEquals(25, bundle.getInt("duplicates_found"))
        assertEquals(1_048_576L, bundle.getLong("reclaimable_bytes"))
    }

    // ── logScanCancelled ──────────────────────────────────────────────────────

    @Test
    fun `logScanCancelled logs scan_cancelled event`() {
        manager.logScanCancelled("VIDEO")
        assertEquals("scan_cancelled", capturedEvent.captured)
    }

    @Test
    fun `logScanCancelled includes scan_type parameter`() {
        manager.logScanCancelled("APK")
        assertEquals("APK", capturedBundle.captured?.getString("scan_type"))
    }

    // ── logFilesDeleted ───────────────────────────────────────────────────────

    @Test
    fun `logFilesDeleted logs files_deleted event`() {
        manager.logFilesDeleted("JUNK", 5, 204_800L)
        assertEquals("files_deleted", capturedEvent.captured)
    }

    @Test
    fun `logFilesDeleted includes all parameters`() {
        manager.logFilesDeleted("IMAGE", 12, 3_000_000L)
        val bundle = capturedBundle.captured!!
        assertEquals("IMAGE", bundle.getString("scan_type"))
        assertEquals(12, bundle.getInt("deleted_count"))
        assertEquals(3_000_000L, bundle.getLong("freed_bytes"))
    }

    // ── logAutoClearInitiated ─────────────────────────────────────────────────

    @Test
    fun `logAutoClearInitiated logs auto_clear_initiated event`() {
        manager.logAutoClearInitiated("JUNK", 65536L)
        assertEquals("auto_clear_initiated", capturedEvent.captured)
    }

    @Test
    fun `logAutoClearInitiated includes scan_type and reclaimable_bytes`() {
        manager.logAutoClearInitiated("VIDEO", 2_000_000L)
        val bundle = capturedBundle.captured!!
        assertEquals("VIDEO", bundle.getString("scan_type"))
        assertEquals(2_000_000L, bundle.getLong("reclaimable_bytes"))
    }

    // ── logSmartCleanupViewed ─────────────────────────────────────────────────

    @Test
    fun `logSmartCleanupViewed logs smart_cleanup_viewed event`() {
        manager.logSmartCleanupViewed()
        assertEquals("smart_cleanup_viewed", capturedEvent.captured)
    }

    @Test
    fun `logSmartCleanupViewed passes null bundle`() {
        manager.logSmartCleanupViewed()
        assertNull(capturedBundle.captured)
    }

    // ── logTreemapViewed ──────────────────────────────────────────────────────

    @Test
    fun `logTreemapViewed logs treemap_viewed event`() {
        manager.logTreemapViewed()
        assertEquals("treemap_viewed", capturedEvent.captured)
    }

    // ── logFeedbackSubmitted ──────────────────────────────────────────────────

    @Test
    fun `logFeedbackSubmitted logs feedback_submitted event`() {
        manager.logFeedbackSubmitted("BUG")
        assertEquals("feedback_submitted", capturedEvent.captured)
    }

    @Test
    fun `logFeedbackSubmitted includes feedback_type parameter`() {
        manager.logFeedbackSubmitted("FEATURE")
        assertEquals("FEATURE", capturedBundle.captured?.getString("feedback_type"))
    }

    // ── logFeatureRequested ───────────────────────────────────────────────────

    @Test
    fun `logFeatureRequested logs feature_requested event`() {
        manager.logFeatureRequested()
        assertEquals("feature_requested", capturedEvent.captured)
    }

    // ── logPrivacyPolicyViewed ────────────────────────────────────────────────

    @Test
    fun `logPrivacyPolicyViewed logs privacy_policy_viewed event`() {
        manager.logPrivacyPolicyViewed()
        assertEquals("privacy_policy_viewed", capturedEvent.captured)
    }

    // ── logLogin ──────────────────────────────────────────────────────────────

    @Test
    fun `logLogin logs login_success event`() {
        manager.logLogin("GOOGLE")
        assertEquals("login_success", capturedEvent.captured)
    }

    @Test
    fun `logLogin includes login_method parameter`() {
        manager.logLogin("FACEBOOK")
        assertEquals("FACEBOOK", capturedBundle.captured?.getString("login_method"))
    }

    // ── logLogout ─────────────────────────────────────────────────────────────

    @Test
    fun `logLogout logs logout event`() {
        manager.logLogout()
        assertEquals("logout", capturedEvent.captured)
    }

    // ── logSettingChanged ─────────────────────────────────────────────────────

    @Test
    fun `logSettingChanged logs settings_changed event`() {
        manager.logSettingChanged("THEME", "DARK")
        assertEquals("settings_changed", capturedEvent.captured)
    }

    @Test
    fun `logSettingChanged includes setting_name and setting_value`() {
        manager.logSettingChanged("LANGUAGE", "hi")
        val bundle = capturedBundle.captured!!
        assertEquals("LANGUAGE", bundle.getString("setting_name"))
        assertEquals("hi", bundle.getString("setting_value"))
    }

    // ── logTutorialInteraction ────────────────────────────────────────────────

    @Test
    fun `logTutorialInteraction logs tutorial_interaction event`() {
        manager.logTutorialInteraction("DASHBOARD", "VIEWED")
        assertEquals("tutorial_interaction", capturedEvent.captured)
    }

    @Test
    fun `logTutorialInteraction includes tutorial_name and tutorial_action`() {
        manager.logTutorialInteraction("LONG_PRESS", "COMPLETED")
        val bundle = capturedBundle.captured!!
        assertEquals("LONG_PRESS", bundle.getString("tutorial_name"))
        assertEquals("COMPLETED", bundle.getString("tutorial_action"))
    }

    // ── logDeepLinkOpened ─────────────────────────────────────────────────────

    @Test
    fun `logDeepLinkOpened logs deep_link_opened event`() {
        manager.logDeepLinkOpened("scanner/image")
        assertEquals("deep_link_opened", capturedEvent.captured)
    }

    @Test
    fun `logDeepLinkOpened includes route parameter`() {
        manager.logDeepLinkOpened("settings/theme")
        assertEquals("settings/theme", capturedBundle.captured?.getString("route"))
    }

    // ── logError ──────────────────────────────────────────────────────────────

    @Test
    fun `logError logs error_encountered event`() {
        manager.logError("IMAGE", "OutOfMemoryError")
        assertEquals("error_encountered", capturedEvent.captured)
    }

    @Test
    fun `logError includes scan_type and error_message`() {
        manager.logError("VIDEO", "NullPointerException")
        val bundle = capturedBundle.captured!!
        assertEquals("VIDEO", bundle.getString("scan_type"))
        assertEquals("NullPointerException", bundle.getString("error_message"))
    }

    // ── logImagePreviewed ─────────────────────────────────────────────────────

    @Test
    fun `logImagePreviewed logs image_previewed event`() {
        manager.logImagePreviewed()
        assertEquals("image_previewed", capturedEvent.captured)
    }

    // ── logScreenView ─────────────────────────────────────────────────────────

    @Test
    fun `logScreenView logs screen_view_custom event`() {
        manager.logScreenView("Dashboard")
        assertEquals("screen_view_custom", capturedEvent.captured)
    }

    @Test
    fun `logScreenView includes screen_name parameter`() {
        manager.logScreenView("Settings")
        assertEquals("Settings", capturedBundle.captured?.getString("screen_name"))
    }

    // ── logContactMerge ───────────────────────────────────────────────────────

    @Test
    fun `logContactMerge logs contact_merge event`() {
        manager.logContactMerge(3)
        assertEquals("contact_merge", capturedEvent.captured)
    }

    @Test
    fun `logContactMerge includes merged_count parameter`() {
        manager.logContactMerge(7)
        assertEquals(7, capturedBundle.captured?.getInt("merged_count"))
    }
}
