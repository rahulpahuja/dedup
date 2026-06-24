package com.rp.dedup

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rp.dedup.UIConstants.ROUTE_ABOUT
import com.rp.dedup.UIConstants.ROUTE_ACTIVITY
import com.rp.dedup.UIConstants.ROUTE_CACHE_CLEANER
import com.rp.dedup.UIConstants.ROUTE_CLEANUP
import com.rp.dedup.UIConstants.ROUTE_DASHBOARD
import com.rp.dedup.UIConstants.ROUTE_FILE_BROWSER
import com.rp.dedup.UIConstants.ROUTE_FILE_SCANNER
import com.rp.dedup.UIConstants.ROUTE_IMAGE_SCANNER
import com.rp.dedup.UIConstants.ROUTE_LOGIN
import com.rp.dedup.UIConstants.ROUTE_PRIVACY_POLICY
import com.rp.dedup.UIConstants.ROUTE_RESULTS_MEDIA
import com.rp.dedup.UIConstants.ROUTE_SCAN_HISTORY
import com.rp.dedup.UIConstants.ROUTE_SETTINGS
import com.rp.dedup.UIConstants.ROUTE_BIG_FILE_MAP
import com.rp.dedup.UIConstants.ROUTE_DEEP_OPTIMIZATION
import com.rp.dedup.UIConstants.ROUTE_EMPTY_FOLDER
import com.rp.dedup.UIConstants.ROUTE_SMART_JUNK
import com.rp.dedup.UIConstants.ROUTE_SOCIAL_MEDIA_CLEANER
import com.rp.dedup.UIConstants.ROUTE_SPLASH
import com.rp.dedup.UIConstants.ROUTE_WHATSAPP_CLEANER
import com.rp.dedup.UIConstants.ROUTE_CONTACT_TEST
import com.rp.dedup.UIConstants.ROUTE_CONTACT_DEDUP
import com.rp.dedup.UIConstants.ROUTE_VIDEO_SCANNER
import com.rp.dedup.UIConstants.ROUTE_VOICE_STORAGE
import com.rp.dedup.feature.voicestorage.presentation.DeDupChatScreen
import com.rp.dedup.core.permissions.AllFilesPermissionGate
import com.rp.dedup.core.permissions.PermissionGate
import com.rp.dedup.core.permissions.PermissionManager
import com.rp.dedup.core.viewmodels.UserProfileViewModel
import com.rp.dedup.screens.*
import com.rp.dedup.ui.theme.DeDupTheme

// Provides DrawerState to any composable in the tree without prop drilling
val LocalDrawerState = compositionLocalOf<DrawerState> { error("No DrawerState provided") }

// Provides the Activity-scoped UserProfileViewModel so guest-mode checks are consistent
// across all NavGraph destinations (viewModel() inside composable{} would return a
// NavBackStackEntry-scoped instance, which is a different object and always has isGuest=false)
val LocalUserProfileViewModel = compositionLocalOf<UserProfileViewModel> { error("No UserProfileViewModel provided") }

sealed class Screen(val route: String) {
    object Splash : Screen(ROUTE_SPLASH)
    object Login : Screen(ROUTE_LOGIN)
    object Dashboard : Screen(ROUTE_DASHBOARD)
    object Cleanup : Screen(ROUTE_CLEANUP)
    object ContactDedup : Screen(ROUTE_CONTACT_DEDUP)
    object ImageScanner : Screen(UIConstants.ROUTE_IMAGE_SCANNER)
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
    object SmartJunk : Screen(ROUTE_SMART_JUNK)
    object PrivacyPolicy : Screen(ROUTE_PRIVACY_POLICY)
    object DeepOptimization : Screen(ROUTE_DEEP_OPTIMIZATION)
    object SocialMediaCleaner : Screen(ROUTE_SOCIAL_MEDIA_CLEANER)
    object EmptyFolder : Screen(ROUTE_EMPTY_FOLDER)
    object BigFileMap : Screen(ROUTE_BIG_FILE_MAP)
    object WhatsAppCleaner : Screen(ROUTE_WHATSAPP_CLEANER)
    object ContactTest : Screen(ROUTE_CONTACT_TEST)
    object VoiceStorage : Screen(ROUTE_VOICE_STORAGE)
}

@Composable
fun AppNavHost(navController: NavHostController, hasPendingDeepLink: Boolean = false) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    
    // Shared ViewModel to ensure profile updates are reflected everywhere
    val profileViewModel: UserProfileViewModel = viewModel()

    // Routes that show the persistent bottom navigation bar
    val showBottomNav = currentRoute == Screen.Dashboard.route
            || currentRoute == Screen.Cleanup.route
            || currentRoute == Screen.VideoScanner.route
            || currentRoute == Screen.Settings.route
            || currentRoute == Screen.ScanHistory.route
            || currentRoute == Screen.FileBrowser.route
            || currentRoute == Screen.ContactDedup.route
            || currentRoute?.startsWith("file_scanner") == true

    CompositionLocalProvider(
        LocalDrawerState provides drawerState,
        LocalUserProfileViewModel provides profileViewModel
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentRoute != Screen.Splash.route && currentRoute != Screen.Login.route,
            drawerContent = {
                AppDrawerContent(
                    navController = navController,
                    drawerState = drawerState,
                    scope = scope,
                    profileViewModel = profileViewModel
                )
            }
        ) {
            Scaffold(
                bottomBar = {
                    if (showBottomNav) BottomNavigationBar(navController)
                }
            ) { innerPadding ->
            PermissionGate(
                permissions = PermissionManager.NOTIFICATIONS,
                rationaleTitle = "Notifications Required",
                rationaleMessage = "To keep your storage clean and alert you of large duplicates, DeDup requires notification access. If previously denied, please enable it in Settings."
            ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(400)
                    ) + fadeIn(animationSpec = tween(400))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(400)
                    ) + fadeOut(animationSpec = tween(400))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(400)
                    ) + fadeIn(animationSpec = tween(400))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(400)
                    ) + fadeOut(animationSpec = tween(400))
                }
            ) {
                composable(Screen.Splash.route) {
                    SplashScreen(navController, hasPendingDeepLink = hasPendingDeepLink)
                }
                composable(Screen.Login.route) {
                    LoginScreen(navController, profileViewModel)
                }
                composable(Screen.Dashboard.route) {
                    DashboardScreen(navController, profileViewModel)
                }
                composable(Screen.SmartJunk.route) {
                    SmartJunkScreen(navController)
                }
                composable(Screen.ContactDedup.route) {
                    ContactScannerGatekeeper(navController)
                }
                composable(Screen.ContactTest.route) {
                    ContactTestScreen(navController)
                }
                composable(Screen.PrivacyPolicy.route) {
                    PrivacyPolicyScreen(navController)
                }
                composable(Screen.Cleanup.route) {
                    FileCleanupScreen(navController)
                }
                // Image scanner — gated behind permission
                composable(Screen.ImageScanner.route) {
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
                composable(Screen.DeepOptimization.route) {
                    DeepSystemOptimizationScreen(navController)
                }
                // Social Media Cleaner uses MediaStore-only queries — no broad storage permission needed.
                composable(Screen.SocialMediaCleaner.route) {
                    SocialMediaCleanerScreen(navController)
                }
                // Empty Folder handles its own SAF grant on Android 11+ (see EmptyFolderScreen).
                composable(Screen.EmptyFolder.route) {
                    EmptyFolderScreen(navController)
                }
                // BigFileMap uses MediaStore on Android 11+; File API + READ_EXTERNAL_STORAGE on older.
                composable(Screen.BigFileMap.route) {
                    BigFileMapScreen(navController)
                }
                // WhatsApp Cleaner uses MediaStore for images/videos/documents — no raw filesystem access.
                composable(Screen.WhatsAppCleaner.route) {
                    WhatsAppCleanerScreen(navController)
                }
                composable(Screen.VoiceStorage.route) {
                    VoiceStorageGatekeeper(navController)
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
            } // PermissionGate
            } // Scaffold innerPadding
        } // ModalNavigationDrawer
    } // CompositionLocalProvider
}

@Composable
fun ContactScannerGatekeeper(navController: NavHostController) {
    PermissionGate(
        permissions      = PermissionManager.CONTACTS,
        rationaleTitle   = "Contacts Access Needed",
        rationaleMessage = "DeDup needs access to your contacts to find and merge duplicate entries."
    ) {
        DeduplicationScreen(navController = navController)
    }
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
    AllFilesPermissionGatekeeper(
        rationaleTitle   = "All Files Access Needed",
        rationaleMessage = "DeDup needs full storage access to browse and manage files across all folders on your device."
    ) {
        FileBrowserScreen(navController = navController)
    }
}

@Composable
fun VoiceStorageGatekeeper(navController: NavHostController) {
    val mediaPermissions = (PermissionManager.IMAGE + PermissionManager.VIDEO + PermissionManager.AUDIO).distinct()
    PermissionGate(
        permissions      = mediaPermissions,
        rationaleTitle   = "Storage Access Needed",
        rationaleMessage = "DeDup AI needs access to your photos, videos, and audio to answer questions about your storage."
    ) {
        DeDupChatScreen(onNavigateUp = { navController.navigateUp() })
    }
}

@Composable
fun AllFilesPermissionGatekeeper(
    rationaleTitle: String,
    rationaleMessage: String,
    content: @Composable () -> Unit
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        AllFilesPermissionGate(
            rationaleTitle = rationaleTitle,
            rationaleMessage = rationaleMessage,
            content = content
        )
    } else {
        PermissionGate(
            permissions = PermissionManager.FILES,
            rationaleTitle = rationaleTitle,
            rationaleMessage = rationaleMessage,
            content = content
        )
    }
}

@Composable
fun FileScannerGatekeeper(
    navController: NavHostController,
    type: String,
    extensions: List<String>
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        AllFilesPermissionGate(
            rationaleTitle   = "Storage Access Needed",
            rationaleMessage = "To scan for duplicate ${type.uppercase()} files across your device, DeDup needs 'All Files Access'."
        ) {
            FileScannerScreen(navController, type, extensions)
        }
    } else {
        PermissionGate(
            permissions      = PermissionManager.FILES,
            rationaleTitle   = "Storage Access Needed",
            rationaleMessage = "DeDup needs storage access to scan for duplicate ${type.uppercase()} files."
        ) {
            FileScannerScreen(navController, type, extensions)
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun AppNavHostPreview() {
    DeDupTheme {
        val navController = rememberNavController()
        AppNavHost(navController = navController)
    }
}
