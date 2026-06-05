package com.rp.dedup

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

// Global state to track deep link route across activity instances/recompositions
var pendingDeepLinkRoute by mutableStateOf<String?>(null)

class MainActivity : AppCompatActivity() {
    private val themeViewModel: ThemeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ThemeViewModel(DataStoreManager(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        // Handle initial intent
        handleIntent(intent)

        val rootResult = RootDetectionManager.check(applicationContext)
        
        val analyticsManager = AnalyticsManager(applicationContext)
        if (rootResult.isRooted) {
            val bundle = Bundle().apply {
                putString("triggered_checks", rootResult.triggeredChecks.joinToString())
            }
            com.google.firebase.analytics.FirebaseAnalytics.getInstance(applicationContext)
                .logEvent("security_rooted_device", bundle)
        }

        setContent {
            val scope = rememberCoroutineScope()
            DeDupTheme(darkTheme = themeViewModel.isDarkTheme()) {

                if (rootResult.isRooted) {
                    RootedDeviceScreen(triggeredChecks = rootResult.triggeredChecks)
                } else {
                    val navController: NavHostController = rememberNavController()
                    val snackbarHostState = remember { SnackbarHostState() }
                    
                    // Handle deep links and handoffs
                    LaunchedEffect(pendingDeepLinkRoute) {
                        pendingDeepLinkRoute?.let { route ->
                            analyticsManager.logDeepLinkOpened(route)
                            
                            val handoffScanType = intent?.getStringExtra("handoff_scan_type")
                            if (handoffScanType != null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Resuming $handoffScanType scan from other device",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }

                            navController.navigate(route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                            pendingDeepLinkRoute = null 
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavHost(navController = navController)
                        
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
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
            pendingDeepLinkRoute = route
        }
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
