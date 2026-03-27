package com.rp.dedup

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.rp.dedup.core.image.PermissionRequester
import com.rp.dedup.screens.*

// Provides DrawerState to any composable in the tree without prop drilling
val LocalDrawerState = compositionLocalOf<DrawerState> { error("No DrawerState provided") }

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Dashboard : Screen("dashboard")
    object Cleanup : Screen("cleanup")
    object ResultsContacts : Screen("results_contacts")
    object ResultsMedia : Screen("results_media")
    object Activity : Screen("activity")
    object VideoScanner : Screen("video_scanner")
}

@Composable
fun AppNavHost(navController: NavHostController) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    CompositionLocalProvider(LocalDrawerState provides drawerState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // Disable swipe gesture on splash so users don't accidentally open the drawer
            gesturesEnabled = currentRoute != Screen.Splash.route,
            drawerContent = {
                AppDrawerContent(
                    navController = navController,
                    drawerState = drawerState,
                    scope = scope
                )
            }
        ) {
            NavHost(navController = navController, startDestination = Screen.Splash.route) {
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
            }
        }
    }
}

@Composable
fun ImageScannerGatekeeper(navController: NavHostController) {
    var hasPermission by remember { mutableStateOf(false) }

    if (hasPermission) {
        ImageScannerScreen(navController = navController)
    } else {
        PermissionRequester(onPermissionGranted = { hasPermission = true })
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Waiting for gallery permission to scan images...")
        }
    }
}

@Composable
fun VideoScannerGatekeeper(navController: NavHostController) {
    var hasPermission by remember { mutableStateOf(false) }

    val videoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    if (hasPermission) {
        VideoScannerScreen(navController = navController)
    } else {
        PermissionRequester(
            onPermissionGranted = { hasPermission = true },
            tiramisu13Permission = videoPermission
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Waiting for permission to scan videos...")
        }
    }
}
