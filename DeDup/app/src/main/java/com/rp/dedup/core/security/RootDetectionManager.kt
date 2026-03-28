package com.rp.dedup.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Multi-layer root detection for DeDup.
 *
 * Combines several independent signals so that a single bypassed check
 * does not hide a rooted device. Each check is isolated — a crash in one
 * never blocks the others.
 *
 * Usage:
 *   val result = RootDetectionManager.check(context)
 *   if (result.isRooted) { /* block the app */ }
 */
object RootDetectionManager {

    data class RootCheckResult(
        val isRooted: Boolean,
        /** Human-readable list of the signals that fired. */
        val triggeredChecks: List<String>
    )

    // ── Known root management app packages ───────────────────────────────────

    private val ROOT_PACKAGES = listOf(
        "com.topjohnwu.magisk",           // Magisk
        "com.noshufou.android.su",         // Superuser
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",            // SuperSU
        "com.koushikdutta.superuser",      // ClockworkMod Superuser
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.kingroot.kinguser",           // KingRoot
        "com.kingo.root",                  // KingoRoot
        "com.smedialink.oneclickroot",
        "com.zhiqupk.root.global",
        "com.alephzain.framaroot",
        "com.koushikdutta.rommanager",
        "com.koushikdutta.rommanager.license",
        "com.dimonvideo.luckypatcher",     // Lucky Patcher
        "com.chelpus.lackypatch",
        "com.ramdroid.appquarantine",
        "com.ramdroid.appquarantine.pro",
        "com.devadvance.rootcloak",        // Root cloaking (itself a signal)
        "com.devadvance.rootcloak2",
        "de.robv.android.xposed.installer",// Xposed Framework
        "com.saurik.substrate",            // Cydia Substrate
        "com.zachspong.temprootremovejb",
        "com.amphoras.hidemyroot",
        "com.formyhm.hiderootPremium",
        "com.amphoras.hidemyrootadfree",
        "com.futuretek.tools",
        "com.jrummy.root.browserfree",
        "com.jrummy.busybox.installer",
        "stericson.busybox",               // BusyBox
        "stericson.busybox.donate",
        "com.masteryu.appbak",
        "com.baidu.duperroot",
        "com.iobit.mobilecare"
    )

    // ── Known su / root binary paths ─────────────────────────────────────────

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/local/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/dev/com.koushikdutta.superuser.daemon/",
        "/system/usr/we-need-root/su-backup",
        "/system/xbin/mu"
    )

    // ── Known root-related files / artefacts ─────────────────────────────────

    private val ROOT_FILES = listOf(
        "/system/app/Superuser.apk",
        "/system/app/SuperSU.apk",
        "/system/etc/init.d/99SuperSUDaemon",
        "/dev/block/superblock",
        "/system/bin/.ext/.su",
        "/system/xbin/daemonsu",
        "/system/etc/security/.su",
        "/sbin/supersu",
        "/su/bin/daemonsu",
        "/magisk/.core/bin/su",             // Magisk artefact
        "/data/adb/magisk",                 // Magisk data directory
        "/cache/magisk.log",
        "/data/adb/ksu"                     // KernelSU
    )

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Runs all checks and returns a [RootCheckResult].
     * Each individual check swallows its own exceptions so the overall
     * result is always available.
     */
    fun check(context: Context): RootCheckResult {
        val triggered = mutableListOf<String>()

        if (checkBuildTags())         triggered += "Test-keys build (unofficial ROM)"
        if (checkSuBinaries())        triggered += "su binary found on device"
        if (checkRootFiles())         triggered += "Root-related system files detected"
        if (checkRootPackages(context)) triggered += "Root management app installed"
        if (checkWritableSystem())    triggered += "/system partition is writable"
        if (checkMagiskSocket())      triggered += "Magisk socket detected"

        return RootCheckResult(
            isRooted = triggered.isNotEmpty(),
            triggeredChecks = triggered
        )
    }

    // ── Individual checks ─────────────────────────────────────────────────────

    /**
     * Official Android builds are signed with release keys; devices running
     * custom ROMs or rooted via fastboot often have "test-keys" in build tags.
     */
    private fun checkBuildTags(): Boolean = runCatching {
        val tags = Build.TAGS ?: return false
        tags.contains("test-keys")
    }.getOrDefault(false)

    /**
     * Checks for the presence of an `su` binary at any of the well-known paths
     * used by common root tools.
     */
    private fun checkSuBinaries(): Boolean = runCatching {
        SU_PATHS.any { path -> File(path).exists() }
    }.getOrDefault(false)

    /**
     * Checks for known root-related APKs and config files left by root tools.
     */
    private fun checkRootFiles(): Boolean = runCatching {
        ROOT_FILES.any { path -> File(path).exists() }
    }.getOrDefault(false)

    /**
     * Queries the package manager for known root management apps.
     * Uses [PackageManager.GET_ACTIVITIES] rather than 0 for better
     * compatibility across API levels.
     */
    private fun checkRootPackages(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        ROOT_PACKAGES.any { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                true
            }.getOrDefault(false)
        }
    }.getOrDefault(false)

    /**
     * On a standard device `/system` is mounted read-only.
     * If we can open it for writing it strongly implies the partition
     * has been remounted as rw by a root tool.
     */
    private fun checkWritableSystem(): Boolean = runCatching {
        val systemFile = File("/system")
        systemFile.canWrite()
    }.getOrDefault(false)

    /**
     * Magisk hides itself from most scanners but leaves a Unix domain socket
     * at a predictable path on the filesystem.
     */
    private fun checkMagiskSocket(): Boolean = runCatching {
        File("/dev/.magisk").exists() ||
        File("/dev/.magisk_unblock").exists() ||
        File("/sbin/.magisk").exists()
    }.getOrDefault(false)
}
