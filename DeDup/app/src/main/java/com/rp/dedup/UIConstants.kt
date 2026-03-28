package com.rp.dedup

object UIConstants {
    // Screen Route Constants
    const val ROUTE_SPLASH = "splash"
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_CLEANUP = "cleanup"
    const val ROUTE_RESULTS_CONTACTS = "results_contacts"
    const val ROUTE_RESULTS_MEDIA = "results_media"
    const val ROUTE_ACTIVITY = "activity"
    const val ROUTE_VIDEO_SCANNER = "video_scanner"
    const val ROUTE_ABOUT = "about"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_SCAN_HISTORY = "scan_history"
    const val ROUTE_FILE_BROWSER = "file_browser"
    const val ROUTE_FILE_SCANNER = "file_scanner/{type}"

    // Helper for parameterized routes
    fun getFileScannerRoute(type: String) = "file_scanner/$type"

    /**
     * Determines the display name for a given route.
     * Uses regions for better organization.
     */
    fun getScreenName(route: String?): String {
        if (route == null) return "DeDuplicator"

        // region Main Screens
        if (route == ROUTE_DASHBOARD) return "Dashboard"
        if (route == ROUTE_CLEANUP) return "File Cleanup"
        if (route == ROUTE_SETTINGS) return "Settings"
        // endregion

        // region Scanners
        if (route == ROUTE_RESULTS_CONTACTS) return "Image Results"
        if (route == ROUTE_VIDEO_SCANNER) return "Video Scanner"
        if (route.startsWith("file_scanner/pdf")) return "PDF Scanner"
        if (route.startsWith("file_scanner/apk")) return "APK Scanner"
        // endregion

        // region Utilities
        if (route == ROUTE_ABOUT) return "About"
        if (route == ROUTE_ACTIVITY) return "Activity Log"
        if (route == ROUTE_SCAN_HISTORY) return "Scan History"
        if (route == ROUTE_FILE_BROWSER) return "File Browser"
        // endregion

        return "DeDuplicator"
    }
}
