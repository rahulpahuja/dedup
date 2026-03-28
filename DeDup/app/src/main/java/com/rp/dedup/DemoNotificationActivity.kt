package com.rp.dedup

import android.os.Bundle
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rp.dedup.core.notifications.AppNotificationManager
import com.rp.dedup.core.notifications.SnackbarManager
import com.rp.dedup.core.notifications.ToastManager
import com.rp.dedup.ui.theme.DeDupTheme

class DemoNotificationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val toastManager = remember { ToastManager(context) }
            val notificationManager = remember { AppNotificationManager(context) }

            NotificationDemoContent(
                toastManager = toastManager,
                notificationManager = notificationManager
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDemoContent(
    toastManager: ToastManager,
    notificationManager: AppNotificationManager
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarManager = remember { SnackbarManager(snackbarHostState, scope) }

    DeDupTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    val customVisuals = data.visuals as? SnackbarManager.CustomSnackbarVisuals
                    if (customVisuals != null) {
                        Snackbar(
                            modifier = Modifier.padding(12.dp),
                            containerColor = if (customVisuals.backgroundColor != Color.Unspecified) 
                                customVisuals.backgroundColor else SnackbarDefaults.color,
                            contentColor = if (customVisuals.contentColor != Color.Unspecified) 
                                customVisuals.contentColor else SnackbarDefaults.contentColor,
                            action = customVisuals.actionLabel?.let {
                                {
                                    TextButton(onClick = { data.performAction() }) {
                                        Text(it, color = customVisuals.contentColor)
                                    }
                                }
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (customVisuals.icon != null) {
                                    Icon(customVisuals.icon, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(customVisuals.message)
                            }
                        }
                    } else {
                        Snackbar(snackbarData = data)
                    }
                }
            },
            topBar = {
                TopAppBar(title = { Text("Notification Demo") })
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Toast Demo", style = MaterialTheme.typography.titleLarge)
                
                Button(onClick = { toastManager.showShort("Simple Short Toast") }) {
                    Text("Show Short Toast")
                }
                
                Button(onClick = { 
                    toastManager.showCustom(
                        message = "Custom Success Toast",
                        iconRes = android.R.drawable.ic_dialog_info,
                        backgroundColor = "#34A853",
                        gravity = Gravity.TOP,
                        yOffset = 200
                    )
                }) {
                    Text("Show Custom Top Toast")
                }

                Spacer(Modifier.height(16.dp))
                Text("Snackbar Demo", style = MaterialTheme.typography.titleLarge)

                Button(onClick = { snackbarManager.showMessage("Simple Snackbar") }) {
                    Text("Show Simple Snackbar")
                }

                Button(onClick = { 
                    snackbarManager.showWithAction(
                        message = "Item Deleted",
                        actionLabel = "Undo",
                        onAction = { toastManager.showShort("Action Clicked!") }
                    )
                }) {
                    Text("Show Snackbar with Action")
                }

                Button(onClick = { 
                    snackbarManager.showCustom(
                        message = "Critical System Error",
                        icon = Icons.Default.Error,
                        backgroundColor = Color.Red,
                        contentColor = Color.White,
                        duration = SnackbarDuration.Long
                    )
                }) {
                    Text("Show Custom Error Snackbar")
                }

                Spacer(Modifier.height(16.dp))
                Text("App Notifications", style = MaterialTheme.typography.titleLarge)

                Button(onClick = { 
                    notificationManager.showActionNotification(
                        id = 1,
                        title = "New Message",
                        description = "You have received a new notification from DeDup."
                    )
                }) {
                    Text("Post Standard Notification")
                }

                Button(onClick = { 
                    notificationManager.showActionNotification(
                        id = 2,
                        title = "Critical Storage Alert",
                        description = "Your storage is almost full. Run a scan now?",
                        isUrgent = true,
                        positiveLabel = "Scan Now",
                        negativeLabel = "Later"
                    )
                }) {
                    Text("Post Urgent Notification")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationDemoPreview() {
    val context = LocalContext.current
    NotificationDemoContent(
        toastManager = ToastManager(context),
        notificationManager = AppNotificationManager(context)
    )
}
