package com.rp.dedup.core.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Renders [content] only after all [permissions] are granted.
 *
 * Behaviour:
 *  1. If every permission in [permissions] is already granted → shows [content].
 *  2. If any permission is missing → fires the system dialog automatically.
 *  3. If the user dismisses the dialog without granting → shows a rationale card
 *     with a "Grant Access" button that re-triggers the dialog.
 *
 * Example:
 *   PermissionGate(
 *       permissions     = PermissionManager.IMAGE,
 *       rationaleTitle   = "Gallery Access Needed",
 *       rationaleMessage = "We need access to scan for duplicate images."
 *   ) {
 *       ImageScannerScreen(navController)
 *   }
 */
@Composable
fun PermissionGate(
    permissions: List<String>,
    rationaleTitle: String   = "Permission Required",
    rationaleMessage: String = "Grant access so this feature can work.",
    content: @Composable () -> Unit
) {
    if (permissions.isEmpty()) { content(); return }

    val context = LocalContext.current
    val manager = remember { PermissionManager(context) }

    var allGranted by remember {
        mutableStateOf(manager.filterDenied(permissions).isEmpty())
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Fix for java.util.ConcurrentModificationException:
        // Defer the state update to avoid registering/unregistering launchers 
        // during the activity result delivery phase.
        val granted = results.values.all { it }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            allGranted = granted
        }
    }

    // Use a reference to the launcher to safely call it from effects or callbacks
    val latestLauncher = rememberUpdatedState(launcher)

    // Auto-fire system dialog on first composition
    LaunchedEffect(allGranted) {
        if (!allGranted) {
            val denied = manager.filterDenied(permissions)
            if (denied.isNotEmpty()) {
                latestLauncher.value.launch(denied.toTypedArray())
            }
        }
    }

    if (allGranted) {
        content()
    } else {
        PermissionDeniedCard(
            title   = rationaleTitle,
            message = rationaleMessage,
            onRetry = {
                val denied = manager.filterDenied(permissions)
                if (denied.isNotEmpty()) {
                    // Check if it's notifications and if we should redirect to settings
                    if (permissions.contains(android.Manifest.permission.POST_NOTIFICATIONS)) {
                        val activity = context as? android.app.Activity
                        if (activity != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.POST_NOTIFICATIONS)) {
                            // User has denied permanently or "Don't ask again" 
                            // OR it's the first time and we should try launcher first.
                            // To be safe, try launcher first, then use a settings intent.
                            latestLauncher.value.launch(denied.toTypedArray())
                            
                            // If they click again and it's still denied, they'll see this logic.
                            // Better approach: use a helper to open settings.
                            // Handle older versions vs newer versions for notification settings
                            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            } else {
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                            }
                            context.startActivity(intent)
                        } else {
                            latestLauncher.value.launch(denied.toTypedArray())
                        }
                    } else {
                        latestLauncher.value.launch(denied.toTypedArray())
                    }
                }
            }
        )
    }
}

// ── Internal UI ───────────────────────────────────────────────────────────────

@Composable
private fun PermissionDeniedCard(
    title: String,
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector    = Icons.Default.Lock,
                            contentDescription = null,
                            tint           = MaterialTheme.colorScheme.primary,
                            modifier       = Modifier.size(28.dp)
                        )
                    }
                }

                // Title
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Body
                Text(
                    text      = message,
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                // Retry button
                Button(
                    onClick  = onRetry,
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val buttonText = if (title.contains("Notifications", ignoreCase = true)) {
                        "Open Settings"
                    } else {
                        "Grant Access"
                    }
                    Text(buttonText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
