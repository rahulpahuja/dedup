package com.rp.dedup

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.rp.dedup.core.firebase.auth.FirebaseAuthManager
import com.rp.dedup.core.notifications.AppNotificationManager
import com.rp.dedup.core.notifications.SnackbarManager
import com.rp.dedup.core.notifications.ToastManager
import com.rp.dedup.ui.theme.DeDupTheme
import kotlinx.coroutines.launch

// ── Test credentials (pre-filled for quick manual testing) ───────────────────
private const val TEST_GOOGLE_EMAIL    = "rahul.pahuja.dev@gmail.com"
private const val TEST_EMAIL_PASSWORD  = "Test@123456"
private const val TEST_PHONE_NUMBER    = "+919876543210"

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

    // Pre-filled with test credentials for fast iteration
    var email by remember { mutableStateOf(TEST_GOOGLE_EMAIL) }
    var password by remember { mutableStateOf(TEST_EMAIL_PASSWORD) }
    var phoneNumber by remember { mutableStateOf(TEST_PHONE_NUMBER) }
    var verificationCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var currentUser by remember { mutableStateOf(authManager.currentUser) }
    var isGoogleSignInLoading by remember { mutableStateOf(false) }
    var isFacebookSignInLoading by remember { mutableStateOf(false) }

    // Detect which provider the current user signed in with
    val signedInWithGoogle = currentUser?.providerData
        ?.any { it.providerId == "google.com" } == true
    val signedInWithFacebook = currentUser?.providerData
        ?.any { it.providerId == "facebook.com" } == true

    // Facebook Login setup
    val callbackManager = remember { CallbackManager.Factory.create() }

    // Register the FacebookCallback on callbackManager once.
    // DisposableEffect ensures the callback is cleared when this composable leaves the tree,
    // preventing stale callbacks if the screen is re-composed.
    DisposableEffect(Unit) {
        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    scope.launch {
                        isFacebookSignInLoading = true
                        currentUser = authManager.signInWithFacebook(result.accessToken.token)
                        isFacebookSignInLoading = false
                    }
                }
                override fun onCancel() {
                    toastManager.showShort("Facebook sign-in cancelled")
                    isFacebookSignInLoading = false
                }
                override fun onError(error: FacebookException) {
                    toastManager.showLong("Facebook sign-in error: ${error.message}")
                    isFacebookSignInLoading = false
                }
            }
        )
        onDispose {
            // LoginManager has no unregisterCallback — registering null clears the slot.
            LoginManager.getInstance().registerCallback(callbackManager, null)
        }
    }

//    // createActivityResultContract() (no-arg) does NOT auto-dispatch to callbackManager.
//    // We forward the result manually so the FacebookCallback above fires exactly once.
//    val facebookLauncher = rememberLauncherForActivityResult(
//        LoginManager.getInstance().createActivityResultContract()
//    ) { result ->
//        callbackManager.onActivityResult(result.requestCode, result.resultCode, result.data)
//    }

    // Pass the callbackManager INTO the contract.
// It will automatically intercept the result and trigger your FacebookCallback.
    val facebookLauncher = rememberLauncherForActivityResult(
        contract = LoginManager.getInstance().createLogInActivityResultContract(callbackManager, null)
    ) {
        // You don't need to manually forward anything here!
        // The callbackManager has already intercepted the result.
    }

    // Phone Auth callbacks
    val phoneAuthCallbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                toastManager.showShort("Phone verification completed automatically")
            }
            override fun onVerificationFailed(e: FirebaseException) {
                toastManager.showLong("Phone verification failed: ${e.message}")
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                toastManager.showShort("OTP sent to $phoneNumber")
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

                // ── Notifications ──────────────────────────────────────────
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

                // ── Firebase Auth ──────────────────────────────────────────
                Text("Firebase Auth Demo", style = MaterialTheme.typography.titleLarge)

                if (currentUser != null) {
                    // ── Logged-in card ──
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Signed In",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.weight(1f))
                                // Provider badge
                                val providerLabel = when {
                                    signedInWithGoogle -> "Google"
                                    signedInWithFacebook -> "Facebook"
                                    currentUser?.isAnonymous == true -> "Anonymous"
                                    currentUser?.phoneNumber != null -> "Phone"
                                    else -> "Email"
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        providerLabel,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                currentUser?.email ?: currentUser?.phoneNumber ?: "UID: ${currentUser?.uid}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (currentUser?.displayName?.isNotBlank() == true) {
                                Text(
                                    "Name: ${currentUser?.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "UID: ${currentUser?.uid}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                if (signedInWithGoogle) {
                                    // Clear Credential Manager state so the account
                                    // picker resets properly for the next Google sign-in
                                    authManager.signOutWithCredentialClear(activity)
                                } else if (signedInWithFacebook) {
                                    LoginManager.getInstance().logOut()
                                    authManager.signOut()
                                } else {
                                    authManager.signOut()
                                }
                                currentUser = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign Out")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                authManager.deleteUser()
                                currentUser = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Account")
                    }

                } else {

                    // ── Social Logins ──────────────────────────────────────
                    Text("Social Sign-In", style = MaterialTheme.typography.titleSmall)

                    // Google
                    Button(
                        onClick = {
                            scope.launch {
                                isGoogleSignInLoading = true
                                currentUser = authManager.signInWithGoogle(activity)
                                isGoogleSignInLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isGoogleSignInLoading
                    ) {
                        if (isGoogleSignInLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(painter = painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isGoogleSignInLoading) "Signing in…" else "Sign in with Google")
                    }

                    // Facebook
                    Button(
                        onClick = {
                            isFacebookSignInLoading = true
                            facebookLauncher.launch(listOf("email", "public_profile"))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isFacebookSignInLoading
                    ) {
                        if (isFacebookSignInLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            // Facebook doesn't have a default Material icon, use branded if available
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isFacebookSignInLoading) "Signing in…" else "Sign in with Facebook")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ── Email / Password ────────────────────────────────────
                    Text("Email & Password", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { scope.launch { currentUser = authManager.signInWithEmail(email, password) } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Login") }
                        Button(
                            onClick = { scope.launch { currentUser = authManager.createUserWithEmail(email, password) } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Register") }
                    }

                    TextButton(
                        onClick = { scope.launch { authManager.sendPasswordResetEmail(email) } },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Forgot Password?") }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ── Phone Auth ──────────────────────────────────────────
                    Text("Phone Authentication", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone (with country code)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (verificationId == null) {
                        Button(
                            onClick = { authManager.verifyPhoneNumber(phoneNumber, activity, phoneAuthCallbacks) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Send OTP") }
                    } else {
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = { verificationCode = it },
                            label = { Text("Enter OTP") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    currentUser = authManager.signInWithPhoneCode(verificationId!!, verificationCode)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Verify OTP & Sign In") }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // ── Anonymous ───────────────────────────────────────────
                    Button(
                        onClick = { scope.launch { currentUser = authManager.signInAnonymously() } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Login Anonymously")
                    }
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
