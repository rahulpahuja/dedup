package com.rp.dedup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.appcompat.app.AppCompatActivity
import com.rp.dedup.core.analytics.AnalyticsManager
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.security.RootDetectionManager
import com.rp.dedup.core.viewmodels.ThemeViewModel
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val themeViewModel: ThemeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ThemeViewModel(DataStoreManager(applicationContext)) as T
            }
        }
    }

    // Per-instance deep link state: eliminates the process-global var that was shared
    // across all MainActivity instances (back-stack, task-switching, split-screen).
    private var pendingDeepLinkRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Handle initial intent
        handleIntent(intent)

        setContent {
            val palette by themeViewModel.appPalette.collectAsState()

            // Root detection runs on IO — 26+ File.exists() calls must not block the UI thread.
            var rootResult by remember { mutableStateOf<RootDetectionManager.RootCheckResult?>(null) }
            LaunchedEffect(Unit) {
                val result = withContext(Dispatchers.IO) {
                    RootDetectionManager.check(applicationContext)
                }
                if (result.isRooted) {
                    val bundle = Bundle().apply {
                        putString("triggered_checks", result.triggeredChecks.joinToString())
                    }
                    com.google.firebase.analytics.FirebaseAnalytics
                        .getInstance(applicationContext)
                        .logEvent("security_rooted_device", bundle)
                }
                rootResult = result
            }

            DeDupTheme(darkTheme = themeViewModel.isDarkTheme(), palette = palette) {

                if (rootResult?.isRooted == true) {
                    RootedDeviceScreen(triggeredChecks = rootResult!!.triggeredChecks)
                } else {
                    val navController: NavHostController = rememberNavController()
                    val analyticsManager = remember { AnalyticsManager(applicationContext) }

                    // Navigate to deep-link route once the nav graph is ready
                    LaunchedEffect(pendingDeepLinkRoute) {
                        pendingDeepLinkRoute?.let { route ->
                            analyticsManager.logDeepLinkOpened(route)
                            navController.navigate(route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                            pendingDeepLinkRoute = null
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavHost(
                            navController = navController,
                            hasPendingDeepLink = pendingDeepLinkRoute != null
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("target_route")?.let { route ->
            if (isAllowedDeepLinkRoute(route)) {
                pendingDeepLinkRoute = route
            } else {
                Log.w("MainActivity", "Rejected deep link to unauthorized route: $route")
            }
        }
    }

    companion object {
        // Explicit allowlist — any route not listed here is rejected regardless of source app.
        // Excludes: splash, login (internal flow), results_media (requires prior scan context),
        // and contact_test (dev-only screen).
        private val ALLOWED_DEEP_LINK_ROUTES = setOf(
            UIConstants.ROUTE_DASHBOARD,
            UIConstants.ROUTE_CLEANUP,
            UIConstants.ROUTE_IMAGE_SCANNER,
            UIConstants.ROUTE_VIDEO_SCANNER,
            UIConstants.ROUTE_ACTIVITY,
            UIConstants.ROUTE_SETTINGS,
            UIConstants.ROUTE_SCAN_HISTORY,
            UIConstants.ROUTE_FILE_BROWSER,
            UIConstants.ROUTE_CACHE_CLEANER,
            UIConstants.ROUTE_SMART_JUNK,
            UIConstants.ROUTE_DEEP_OPTIMIZATION,
            UIConstants.ROUTE_SOCIAL_MEDIA_CLEANER,
            UIConstants.ROUTE_EMPTY_FOLDER,
            UIConstants.ROUTE_BIG_FILE_MAP,
            UIConstants.ROUTE_WHATSAPP_CLEANER,
            UIConstants.ROUTE_CONTACT_DEDUP,
            UIConstants.ROUTE_ABOUT,
            UIConstants.ROUTE_PRIVACY_POLICY,
        )

        private fun isAllowedDeepLinkRoute(route: String): Boolean =
            route in ALLOWED_DEEP_LINK_ROUTES ||
                route.startsWith("file_scanner/pdf") ||
                route.startsWith("file_scanner/apk")
    }
}

@Composable
private fun RootedDeviceScreen(triggeredChecks: List<String>) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.GppBad,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Rooted Device Detected",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "DeDup cannot run on a rooted device.\n\n" +
                        "Root access compromises the operating system's security " +
                        "boundaries and puts your files and personal data at risk. " +
                        "To protect your privacy, the app has been blocked.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (triggeredChecks.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "DETECTION SIGNALS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = androidx.compose.ui.unit.TextUnit(
                                    2f, androidx.compose.ui.unit.TextUnitType.Sp
                                )
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        triggeredChecks.forEach { signal ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    "•  ",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    signal,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "If you believe this is a mistake, ensure your device is\n" +
                        "running an unmodified Android build.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
