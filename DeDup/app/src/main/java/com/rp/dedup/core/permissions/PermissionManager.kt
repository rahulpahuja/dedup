package com.rp.dedup.core.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Central permission authority for DeDup.
 *
 * Provides version-aware permission groups and runtime grant-state helpers.
 * All permission groupings are defined in the companion object so they can be
 * referenced without an instance (e.g. inside `PermissionGate` calls).
 *
 * Usage example:
 *   val mgr = PermissionManager(context)
 *   val denied = mgr.filterDenied(PermissionManager.IMAGE)
 *   launcher.launch(denied.toTypedArray())
 */
class PermissionManager(private val context: Context) {

    companion object {

        // ── Media ────────────────────────────────────────────────────────────

        /**
         * Read images.
         * - API 34+ : READ_MEDIA_IMAGES + READ_MEDIA_VISUAL_USER_SELECTED (partial picker)
         * - API 33  : READ_MEDIA_IMAGES
         * - API ≤32 : READ_EXTERNAL_STORAGE
         */
        val IMAGE: List<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        /**
         * Read videos.
         * - API 33+ : READ_MEDIA_VIDEO
         * - API ≤32 : READ_EXTERNAL_STORAGE
         */
        val VIDEO: List<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                listOf(Manifest.permission.READ_MEDIA_VIDEO)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        /**
         * Read audio files.
         * - API 33+ : READ_MEDIA_AUDIO
         * - API ≤32 : READ_EXTERNAL_STORAGE
         */
        val AUDIO: List<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                listOf(Manifest.permission.READ_MEDIA_AUDIO)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        /**
         * Read general files (PDFs, APKs, documents, etc.).
         * - API 33+ : READ_MEDIA_IMAGES + READ_MEDIA_VIDEO (covers MediaStore-indexed files)
         * - API 29–32: READ_EXTERNAL_STORAGE
         * - API ≤28 : READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE
         */
        val FILES: List<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        // ── Communication ────────────────────────────────────────────────────

        /** Read and receive SMS messages. */
        val SMS: List<String> = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )

        // ── System ───────────────────────────────────────────────────────────

        /**
         * Post push notifications.
         * - API 33+ : POST_NOTIFICATIONS (runtime permission)
         * - API ≤32 : empty (auto-granted)
         */
        val NOTIFICATIONS: List<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            else emptyList()

        // ── Compound sets ─────────────────────────────────────────────────────

        /** All media permissions combined, de-duplicated. */
        val ALL_MEDIA: List<String> = (IMAGE + VIDEO + AUDIO + FILES).distinct()

        /** Every runtime permission the app may ever request, de-duplicated. */
        val ALL: List<String> = (ALL_MEDIA + SMS + NOTIFICATIONS).distinct()
    }

    // ── Runtime check helpers ─────────────────────────────────────────────────

    /** Returns `true` if [permission] has been granted. */
    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

    /** Returns `true` if every permission in [permissions] has been granted. */
    fun areAllGranted(permissions: List<String>): Boolean =
        permissions.all { isGranted(it) }

    /** Returns only the permissions from [permissions] that have NOT been granted. */
    fun filterDenied(permissions: List<String>): List<String> =
        permissions.filterNot { isGranted(it) }

    // ── Convenience properties ────────────────────────────────────────────────

    val hasImageAccess: Boolean        get() = areAllGranted(IMAGE)
    val hasVideoAccess: Boolean        get() = areAllGranted(VIDEO)
    val hasAudioAccess: Boolean        get() = areAllGranted(AUDIO)
    val hasFileAccess:  Boolean        get() = areAllGranted(FILES)
    val hasSmsAccess:   Boolean        get() = areAllGranted(SMS)
    val hasNotificationAccess: Boolean get() =
        NOTIFICATIONS.isEmpty() || areAllGranted(NOTIFICATIONS)

    /**
     * Whether the app holds "All Files Access" (MANAGE_EXTERNAL_STORAGE).
     *
     * This is a special permission that CANNOT be requested via the normal
     * `RequestMultiplePermissions` launcher — call [openAllFilesAccessSettings]
     * to direct the user to the system settings page.
     *
     * Returns `true` on API < 30 (not applicable).
     */
    val hasAllFilesAccess: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else true

    /**
     * Opens the system "All Files Access" page for this app.
     * Only does something on API 30+.
     */
    fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }
}
