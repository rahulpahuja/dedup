package com.rp.dedup

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.rp.dedup.UIConstants.ROUTE_ABOUT
import com.rp.dedup.UIConstants.ROUTE_ACTIVITY
import com.rp.dedup.UIConstants.ROUTE_CACHE_CLEANER
import com.rp.dedup.UIConstants.ROUTE_CLEANUP
import com.rp.dedup.UIConstants.ROUTE_DASHBOARD
import com.rp.dedup.UIConstants.ROUTE_FILE_BROWSER
import com.rp.dedup.UIConstants.ROUTE_FILE_SCANNER
import com.rp.dedup.UIConstants.ROUTE_RESULTS_CONTACTS
import com.rp.dedup.UIConstants.ROUTE_RESULTS_MEDIA
import com.rp.dedup.UIConstants.ROUTE_SCAN_HISTORY
import com.rp.dedup.UIConstants.ROUTE_SETTINGS
import com.rp.dedup.UIConstants.ROUTE_SPLASH
import com.rp.dedup.UIConstants.ROUTE_VIDEO_SCANNER
import com.rp.dedup.core.permissions.PermissionGate
import com.rp.dedup.core.permissions.PermissionManager
import com.rp.dedup.screens.*

// Provides DrawerState to any composable in the tree without prop drilling
val LocalDrawerState = compositionLocalOf<DrawerState> { error("No DrawerState provided") }

sealed class Screen(val route: String) {
    object Splash : Screen(ROUTE_SPLASH)
    object Dashboard : Screen(ROUTE_DASHBOARD)
    object Cleanup : Screen(ROUTE_CLEANUP)
    object ResultsContacts : Screen(ROUTE_RESULTS_CONTACTS)
    object ResultsMedia : Screen(ROUTE_RESULTS_MEDIA)
    object Activity : Screen(ROUTE_ACTIVITY)
    object VideoScanner : Screen(ROUTE_VIDEO_SCANNER)
    object About : Screen(ROUTE_ABOUT)
    object Settings : Screen(ROUTE_SETTINGS)
    object ScanHistory : Screen(ROUTE_SCAN_HISTORY)
    object FileBrowser : Screen(ROUTE_FILE_BROWSER)
    object CacheCleaner : Screen(ROUTE_CACHE_CLEANER)
    object FileScanner : Screen(ROUTE_FILE_SCANNER) {
        fun createRoute(type: String) = UIConstants.getFileScannerRoute(type)
    }
}

@Composable
fun AppNavHost(navController: NavHostController) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Routes that show the persistent bottom navigation bar
    val showBottomNav = currentRoute == Screen.Dashboard.route
            || currentRoute == Screen.Cleanup.route
            || currentRoute == Screen.VideoScanner.route
            || currentRoute == Screen.Settings.route
            || currentRoute == Screen.ScanHistory.route
            || currentRoute == Screen.FileBrowser.route
            || currentRoute?.startsWith("file_scanner") == true

    CompositionLocalProvider(LocalDrawerState provides drawerState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentRoute != Screen.Splash.route,
            drawerContent = {
                AppDrawerContent(
                    navController = navController,
                    drawerState = drawerState,
                    scope = scope
                )
            }
        ) {
            Scaffold(
                bottomBar = {
                    if (showBottomNav) BottomNavigationBar(navController)
                }
            ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Splash.route) {
                    SplashScreen(navController)
                }
                composable(Screen.Dashboard.route) {
                    DashboardScreen(navController)
                }
                composable(Screen.Cleanup.route) {
                    FileCleanupScreen(navController)
                }
                // Image scanner — gated behind permission
                composable(Screen.ResultsContacts.route) {
                    ImageScannerGatekeeper(navController)
                }
                composable(Screen.ResultsMedia.route) {
                    DuplicateClustersScreen(navController)
                }
                composable(Screen.Activity.route) {
                    ActivityLogScreen(navController)
                }
                composable(Screen.VideoScanner.route) {
                    VideoScannerGatekeeper(navController)
                }
                composable(Screen.About.route) {
                    AboutScreen(navController)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(navController)
                }
                composable(Screen.ScanHistory.route) {
                    ScanHistoryScreen(navController)
                }
                composable(Screen.FileBrowser.route) {
                    FileBrowserGatekeeper(navController)
                }
                composable(Screen.CacheCleaner.route) {
                    CacheCleanerScreen(navController)
                }
                composable(
                    route = Screen.FileScanner.route,
                    arguments = listOf(navArgument("type") { type = NavType.StringType })
                ) { backStackEntry ->
                    val fileType = backStackEntry.arguments?.getString("type") ?: "pdf"
                    val extensions = if (fileType == "pdf") listOf("pdf") else listOf("apk")
                    FileScannerGatekeeper(navController, fileType, extensions)
                }
            } // NavHost
            } // Scaffold innerPadding
        } // ModalNavigationDrawer
    } // CompositionLocalProvider
}

@Composable
fun ImageScannerGatekeeper(navController: NavHostController) {
    PermissionGate(
        permissions      = PermissionManager.IMAGE,
        rationaleTitle   = "Gallery Access Needed",
        rationaleMessage = "DeDup needs access to your photos to find and remove duplicate images."
    ) {
        ImageScannerScreen(navController = navController)
    }
}

@Composable
fun VideoScannerGatekeeper(navController: NavHostController) {
    PermissionGate(
        permissions      = PermissionManager.VIDEO,
        rationaleTitle   = "Video Access Needed",
        rationaleMessage = "DeDup needs access to your videos to find and remove duplicate recordings."
    ) {
        VideoScannerScreen(navController = navController)
    }
}

@Composable
fun FileBrowserGatekeeper(navController: NavHostController) {
    PermissionGate(
        permissions      = PermissionManager.FILES,
        rationaleTitle   = "Storage Access Needed",
        rationaleMessage = "DeDup needs storage access to browse and manage files on your device."
    ) {
        FileBrowserScreen(navController = navController)
    }
}

@Composable
fun FileScannerGatekeeper(
    navController: NavHostController,
    type: String,
    extensions: List<String>
) {
    PermissionGate(
        permissions      = PermissionManager.FILES,
        rationaleTitle   = "Storage Access Needed",
        rationaleMessage = "DeDup needs storage access to scan for duplicate ${type.uppercase()} files."
    ) {
        FileScannerScreen(navController, type, extensions)
    }
}
