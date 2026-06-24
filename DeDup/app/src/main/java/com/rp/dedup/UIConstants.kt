package com.rp.dedup

import com.rp.dedup.R
import androidx.compose.ui.graphics.Color

object UIConstants {

    // ── Routes ────────────────────────────────────────────────────────────────
    const val ROUTE_SPLASH          = "splash"
    const val ROUTE_LOGIN           = "login"
    const val ROUTE_DASHBOARD       = "dashboard"
    const val ROUTE_CLEANUP         = "cleanup"
    const val ROUTE_IMAGE_SCANNER   = "image_scanner"
    const val ROUTE_RESULTS_MEDIA   = "results_media"
    const val ROUTE_ACTIVITY        = "activity"
    const val ROUTE_VIDEO_SCANNER   = "video_scanner"
    const val ROUTE_ABOUT           = "about"
    const val ROUTE_SETTINGS        = "settings"
    const val ROUTE_SCAN_HISTORY    = "scan_history"
    const val ROUTE_FILE_BROWSER    = "file_browser"
    const val ROUTE_CACHE_CLEANER   = "cache_cleaner"
    const val ROUTE_FILE_SCANNER    = "file_scanner/{type}"
    const val ROUTE_SMART_JUNK              = "smart_junk"
    const val ROUTE_PRIVACY_POLICY          = "privacy_policy"
    const val ROUTE_DEEP_OPTIMIZATION       = "deep_optimization"
    const val ROUTE_SOCIAL_MEDIA_CLEANER    = "social_media_cleaner"
    const val ROUTE_EMPTY_FOLDER            = "empty_folder"
    const val ROUTE_BIG_FILE_MAP            = "big_file_map"
    const val ROUTE_WHATSAPP_CLEANER        = "whatsapp_cleaner"
    const val ROUTE_CONTACT_DEDUP          = "deduplication"
    const val ROUTE_CONTACT_TEST           = "contact_test"
    const val ROUTE_VOICE_STORAGE          = "voice_storage"

    fun getFileScannerRoute(type: String) = "file_scanner/$type"

    fun getScreenNameRes(route: String?): Int {
        if (route == null) return R.string.app_name
        return when {
            route == ROUTE_LOGIN -> R.string.screen_login
            route == ROUTE_DASHBOARD -> R.string.screen_dashboard
            route == ROUTE_CLEANUP -> R.string.screen_file_cleanup
            route == ROUTE_SETTINGS -> R.string.settings_title
            route == ROUTE_IMAGE_SCANNER -> R.string.scanner_title
            route == ROUTE_VIDEO_SCANNER -> R.string.video_scanner_title
            route.startsWith("file_scanner/pdf") -> R.string.pdf_scanner_title
            route.startsWith("file_scanner/apk") -> R.string.apk_scanner_title
            route == ROUTE_ABOUT -> R.string.screen_about
            route == ROUTE_ACTIVITY -> R.string.screen_activity_log
            route == ROUTE_SCAN_HISTORY -> R.string.screen_scan_history
            route == ROUTE_FILE_BROWSER -> R.string.screen_file_browser
            route == ROUTE_CACHE_CLEANER -> R.string.screen_cache_cleaner
            route == ROUTE_SMART_JUNK -> R.string.screen_smart_cleanup
            route == ROUTE_PRIVACY_POLICY -> R.string.screen_privacy_policy
            route == ROUTE_DEEP_OPTIMIZATION -> R.string.screen_deep_optimization
            route == ROUTE_SOCIAL_MEDIA_CLEANER -> R.string.screen_social_media_cleaner
            route == ROUTE_EMPTY_FOLDER -> R.string.screen_empty_folder_remover
            route == ROUTE_BIG_FILE_MAP -> R.string.screen_big_file_map
            route == ROUTE_WHATSAPP_CLEANER -> R.string.screen_whatsapp_cleaner
            route == ROUTE_CONTACT_DEDUP -> R.string.contact_deduplication
            else -> R.string.app_name
        }
    }

    // ── App-level strings ─────────────────────────────────────────────────────
    const val APP_NAME    = "DeDup"
    val APP_VERSION: String get() = "v${BuildConfig.VERSION_NAME}"

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
    const val QUICK_SCAN_SMART_JUNK   = "Smart Cleanup"

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
