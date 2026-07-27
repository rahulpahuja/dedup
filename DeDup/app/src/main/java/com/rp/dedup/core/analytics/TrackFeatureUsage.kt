package com.rp.dedup.core.analytics

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Logs a `feature_opened` event when this enters composition and a matching
 * `feature_closed` event (with elapsed time on-screen) when it leaves.
 * Call once near the top of a screen's root composable.
 */
@Composable
fun TrackFeatureUsage(featureName: String) {
    val context = LocalContext.current
    DisposableEffect(featureName) {
        val analyticsManager = AnalyticsManager.getInstance(context)
        val openedAtMs = SystemClock.elapsedRealtime()
        analyticsManager.logFeatureOpened(featureName)
        onDispose {
            analyticsManager.logFeatureClosed(featureName, SystemClock.elapsedRealtime() - openedAtMs)
        }
    }
}
