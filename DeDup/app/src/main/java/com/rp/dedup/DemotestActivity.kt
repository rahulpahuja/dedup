package com.rp.dedup

import android.app.Activity
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.rp.dedup.core.firebase.auth.FirebaseAuthManager
import com.rp.dedup.core.notifications.AppNotificationManager
import com.rp.dedup.core.notifications.SnackbarManager
import com.rp.dedup.core.notifications.ToastManager
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch

class DemotestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val toastManager = remember { ToastManager(context) }
            val notificationManager = remember { AppNotificationManager(context) }
            val authManager = remember { FirebaseAuthManager(toastManager) }

            NotificationDemoContent(
                toastManager = toastManager,
                notificationManager = notificationManager,
                authManager = authManager
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDemoContent(
    toastManager: ToastManager,
    notificationManager: AppNotificationManager,
    authManager: FirebaseAuthManager
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarManager = remember { SnackbarManager(snackbarHostState, scope) }
    val activity = LocalContext.current as Activity

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var currentUser by remember { mutableStateOf(authManager.currentUser) }

    // Phone Auth Callbacks
    val phoneAuthCallbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                toastManager.showShort("Phone verification completed automatically")
                scope.launch {
                    // Note: This usually handles automatic SMS retrieval
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                toastManager.showLong("Phone verification failed: ${e.message}")
            }

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                toastManager.showShort("OTP Sent to $phoneNumber")
            }
        }
    }

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
                TopAppBar(title = { Text("App Feature Demo") })
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
                // --- Notification Section ---
                Text("Toasts & Notifications", style = MaterialTheme.typography.titleLarge)
                
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

                Button(onClick = { 
                    snackbarManager.showCustom(
                        message = "Custom Error Snackbar",
                        icon = Icons.Default.Error,
                        backgroundColor = Color.Red,
                        contentColor = Color.White
                    )
                }) {
                    Text("Show Custom Snackbar")
                }

                Button(onClick = { 
                    notificationManager.showActionNotification(
                        id = 2,
                        title = "Critical Alert",
                        description = "Urgent task requires your attention.",
                        isUrgent = true
                    )
                }) {
                    Text("Show Urgent Notification")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // --- Firebase Auth Section ---
                Text("Firebase Auth Demo", style = MaterialTheme.typography.titleLarge)
                
                if (currentUser != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Logged in as:", style = MaterialTheme.typography.labelLarge)
                            Text(currentUser?.email ?: "User (UID: ${currentUser?.uid})", style = MaterialTheme.typography.bodyMedium)
                            Text("Phone: ${currentUser?.phoneNumber ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    Button(onClick = { 
                        authManager.signOut()
                        currentUser = null
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Sign Out")
                    }

                    Button(onClick = { 
                        scope.launch {
                            authManager.deleteUser()
                            currentUser = null
                        }
                    }) {
                        Text("Delete Account")
                    }
                } else {
                    // Email/Password
                    Text("Email & Password", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { 
                            scope.launch { currentUser = authManager.signInWithEmail(email, password) }
                        }) { Text("Login") }
                        Button(onClick = { 
                            scope.launch { currentUser = authManager.createUserWithEmail(email, password) }
                        }) { Text("Register") }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Phone Auth
                    Text("Phone Authentication", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number (with country code)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (verificationId == null) {
                        Button(onClick = { 
                            authManager.verifyPhoneNumber(phoneNumber, activity, phoneAuthCallbacks)
                        }) { Text("Send OTP") }
                    } else {
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = { verificationCode = it },
                            label = { Text("Enter OTP") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { 
                            scope.launch {
                                currentUser = authManager.signInWithPhoneCode(verificationId!!, verificationCode)
                            }
                        }) { Text("Verify OTP & Sign In") }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Social Login (Placeholders for Tokens)
                    Text("Social Login (Simulation)", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { toastManager.showShort("Google Sign-In requires actual ID Token") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Google") }
                        
                        Button(
                            onClick = { toastManager.showShort("FB Sign-In requires actual Access Token") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Facebook") }
                    }
                    
                    Button(
                        onClick = { toastManager.showShort("Twitter Sign-In requires actual Auth Tokens") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Twitter (X)") }

                    Button(onClick = { 
                        scope.launch { currentUser = authManager.signInAnonymously() }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Login Anonymously")
                    }

                    TextButton(onClick = { 
                        scope.launch { authManager.sendPasswordResetEmail(email) }
                    }) { Text("Forgot Password?") }
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
        notificationManager = AppNotificationManager(context),
        authManager = FirebaseAuthManager(ToastManager(context))
    )
}
