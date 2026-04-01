package com.rp.dedup

import androidx.compose.ui.graphics.Color

object UIConstants {

    // ── Routes ────────────────────────────────────────────────────────────────
    const val ROUTE_SPLASH          = "splash"
    const val ROUTE_LOGIN           = "login"
    const val ROUTE_DASHBOARD       = "dashboard"
    const val ROUTE_CLEANUP         = "cleanup"
    const val ROUTE_RESULTS_CONTACTS = "results_contacts"
    const val ROUTE_RESULTS_MEDIA   = "results_media"
    const val ROUTE_ACTIVITY        = "activity"
    const val ROUTE_VIDEO_SCANNER   = "video_scanner"
    const val ROUTE_ABOUT           = "about"
    const val ROUTE_SETTINGS        = "settings"
    const val ROUTE_SCAN_HISTORY    = "scan_history"
    const val ROUTE_FILE_BROWSER    = "file_browser"
    const val ROUTE_CACHE_CLEANER   = "cache_cleaner"
    const val ROUTE_FILE_SCANNER    = "file_scanner/{type}"

    fun getFileScannerRoute(type: String) = "file_scanner/$type"

    fun getScreenName(route: String?): String {
        if (route == null) return APP_NAME
        if (route == ROUTE_LOGIN)           return "Login"
        if (route == ROUTE_DASHBOARD)       return "Dashboard"
        if (route == ROUTE_CLEANUP)         return "File Cleanup"
        if (route == ROUTE_SETTINGS)        return "Settings"
        if (route == ROUTE_RESULTS_CONTACTS) return "Image Results"
        if (route == ROUTE_VIDEO_SCANNER)   return "Video Scanner"
        if (route.startsWith("file_scanner/pdf")) return "PDF Scanner"
        if (route.startsWith("file_scanner/apk")) return "APK Scanner"
        if (route == ROUTE_ABOUT)           return "About"
        if (route == ROUTE_ACTIVITY)        return "Activity Log"
        if (route == ROUTE_SCAN_HISTORY)    return "Scan History"
        if (route == ROUTE_FILE_BROWSER)    return "File Browser"
        if (route == ROUTE_CACHE_CLEANER)   return "Cache Cleaner"
        return APP_NAME
    }

    // ── App-level strings ─────────────────────────────────────────────────────
    const val APP_NAME    = "DeDuplicator"
    const val APP_VERSION = "v1.0.0"

    // ── Scan type identifiers (must match DB values) ───────────────────────────
    const val SCAN_TYPE_IMAGE    = "IMAGE"
    const val SCAN_TYPE_VIDEO    = "VIDEO"
    const val SCAN_TYPE_FILE_PDF = "FILE_PDF"
    const val SCAN_TYPE_FILE_APK = "FILE_APK"

    // ── Scan type display labels ───────────────────────────────────────────────
    const val SCAN_LABEL_IMAGE    = "Photos Scan"
    const val SCAN_LABEL_VIDEO    = "Videos Scan"
    const val SCAN_LABEL_PDF      = "Documents Scan"
    const val SCAN_LABEL_APK      = "APKs Scan"
    const val SCAN_LABEL_UNKNOWN  = "File Scan"

    // ── Bottom nav bar labels ─────────────────────────────────────────────────
    const val NAV_LABEL_DASH     = "DASH"
    const val NAV_LABEL_SCAN     = "SCAN"
    const val NAV_LABEL_FILES    = "FILES"
    const val NAV_LABEL_VIDEO    = "VIDEO"
    const val NAV_LABEL_SETTINGS = "SETTINGS"

    // ── Quick-scan grid titles ────────────────────────────────────────────────
    const val QUICK_SCAN_IMAGES       = "Images"
    const val QUICK_SCAN_VIDEOS       = "Videos"
    const val QUICK_SCAN_DOCUMENTS    = "Documents"
    const val QUICK_SCAN_APKS         = "APKs"
    const val QUICK_SCAN_BROWSE_FILES = "Browse Files"
    const val QUICK_SCAN_HISTORY      = "Scan History"

    // ── File browser ──────────────────────────────────────────────────────────
    const val FILE_BROWSER_ROOT_LABEL = "Internal Storage"

    // ── Dark background gradient (Settings / Login / ScanHistory) ─────────────
    val GradientDarkStart = Color(0xFF060D1F)
    val GradientDarkEnd   = Color(0xFF0D2347)

    // ── Dialog surface ────────────────────────────────────────────────────────
    val DialogSurface = Color(0xFF0D1B3E)

    // ── Scan-category / quick-scan card colors ────────────────────────────────
    val ColorImages       = Color(0xFF4285F4)
    val ColorVideos       = Color(0xFFEA4335)
    val ColorDocuments    = Color(0xFFFBBC05)
    val ColorApks         = Color(0xFF34A853)
    val ColorBrowseFiles  = Color(0xFF00ACC1)
    val ColorScanHistory  = Color(0xFF7986CB)

    // ── Status colors ─────────────────────────────────────────────────────────
    val ColorSuccess   = Color(0xFF4CAF50)
    val ColorWarning   = Color(0xFFFF9800)
    val ColorError     = Color(0xFFEA4335)

    // ── Savings / reclaimable green ───────────────────────────────────────────
    val ColorSavingsGreen = Color(0xFF34A853)

    // ── Summary-card stat colors ──────────────────────────────────────────────
    val ColorDuplicatesStat  = Color(0xFFFBC02D)
    val ColorReclaimableStat = Color(0xFF4DB6AC)

    // ── Theme-picker icon colors ──────────────────────────────────────────────
    val ColorThemeLight = Color(0xFFFBC02D)
    val ColorThemeDark  = Color(0xFF7986CB)
    val ColorThemeAuto  = Color(0xFF4DB6AC)

    // ── Settings icon colors ──────────────────────────────────────────────────
    val ColorIconPalette = Color(0xFF9C27B0)
    val ColorIconInfo    = Color(0xFF2196F3)

    // ── Login screen button gradient ──────────────────────────────────────────
    val LoginButtonStart = Color(0xFF0056D2)
    val LoginButtonEnd   = Color(0xFF0099FF)

    // ── Social login brand colors ─────────────────────────────────────────────
    val ColorGoogle   = Color(0xFFEA4335)
    val ColorFacebook = Color(0xFF1877F2)

    // ── File-type icon colors (FileBrowserScreen) ─────────────────────────────
    val ColorFileFolder       = Color(0xFFFBBC05)
    val ColorFileImage        = Color(0xFF4285F4)
    val ColorFileVideo        = Color(0xFFEA4335)
    val ColorFileAudio        = Color(0xFF9C27B0)
    val ColorFilePdf          = Color(0xFFFF5722)
    val ColorFileApk          = Color(0xFF34A853)
    val ColorFileArchive      = Color(0xFF607D8B)
    val ColorFileWordDoc      = Color(0xFF1A73E8)
    val ColorFileSpreadsheet  = Color(0xFF0F9D58)
    val ColorFilePresentation = Color(0xFFFF6D00)
    val ColorFileText         = Color(0xFF78909C)
    val ColorFileGeneric      = Color(0xFF9E9E9E)
}
