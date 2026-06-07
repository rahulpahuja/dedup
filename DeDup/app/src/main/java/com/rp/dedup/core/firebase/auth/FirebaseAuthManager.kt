package com.rp.dedup.core.firebase.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.rp.dedup.core.security.NativeLib
import com.rp.dedup.core.notifications.ToastManager
import com.rp.dedup.core.firebase.db.FirebaseDbManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class FirebaseAuthManager(
    private val toastManager: ToastManager,
    private val dbManager: FirebaseDbManager = FirebaseDbManager(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "FirebaseAuthManager"
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    // ── Email / Password ──────────────────────────────────────────────────────

    suspend fun signInAnonymously(): FirebaseUser? {
        return try {
            Log.d(TAG, "Attempting anonymous sign-in")
            val result = auth.signInAnonymously().await()
            Log.d(TAG, "Anonymous sign-in successful")
            toastManager.showShort("Signed in anonymously")
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed", e)
            toastManager.showLong("Anonymous sign-in failed: ${e.message}")
            null
        }
    }

    suspend fun createUserWithEmail(email: String, password: String): FirebaseUser? {
        return try {
            Log.d(TAG, "Attempting to create user") // Sanitized log
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Log.d(TAG, "User creation successful")
            toastManager.showShort("User account created successfully")
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "User creation failed", e)
            toastManager.showLong("Registration failed: ${e.message}")
            null
        }
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser? {
        return try {
            Log.d(TAG, "Attempting sign-in") // Sanitized log
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Log.d(TAG, "Sign-in successful")
            toastManager.showShort("Signed in successfully")
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            toastManager.showLong("Login failed: ${e.message}")
            null
        }
    }

    // ── Sign in with Google (Credential Manager) ──────────────────────────────

    /**
     * Launches the Credential Manager bottom-sheet to sign the user in with Google,
     * then authenticates with Firebase using the returned ID token.
     */
    suspend fun signInWithGoogle(activityContext: Context): FirebaseUser? {
        return try {
            val idToken = fetchGoogleIdToken(activityContext) ?: return null
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            signInWithCredential(credential, "Google")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Google sign-in flow failed", e)
            toastManager.showLong("Google sign-in failed: ${e.localizedMessage}")
            null
        }
    }

    private suspend fun fetchGoogleIdToken(context: Context): String? {
        if (!isNetworkAvailable(context)) {
            Log.e(TAG, "No internet connection available for Google sign-in")
            toastManager.showLong("Please check your internet connection and try again.")
            return null
        }

        val activity = context.findActivity()
        if (activity == null) {
            Log.e(TAG, "Activity context is required for Credential Manager")
            return null
        }

        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(activity)
        if (resultCode != ConnectionResult.SUCCESS) {
            Log.e(TAG, "Google Play Services not available: $resultCode")
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(activity, resultCode, 9000)?.show()
            } else {
                toastManager.showLong("Google Play Services is not available.")
            }
            return null
        }

        val credentialManager = CredentialManager.create(activity)

        val serverClientId = NativeLib().getGoogleWebClientId()
        
        // Log basic configuration info to help debugging (Obfuscated Client ID)
        val obfuscatedClientId = if (serverClientId.length > 10) 
            "${serverClientId.take(5)}...${serverClientId.takeLast(5)}" else "invalid"
            
        return try {
            // First attempt: GetGoogleIdOption (can use auto-select/One Tap if authorized)
            requestGoogleIdToken(
                credentialManager = credentialManager,
                activity = activity,
                serverClientId = serverClientId,
                filterByAuthorizedAccounts = true
            )
        } catch (e: Exception) {
            val isTransactionTooLarge = e.message?.contains("TransactionTooLargeException", ignoreCase = true) == true
            
            when {
                e is NoCredentialException || isTransactionTooLarge -> {
                    if (isTransactionTooLarge) {
                        Log.w(TAG, "TransactionTooLargeException hit (Android 14+ multiple accounts bug). Falling back.")
                    } else {
                        Log.d(TAG, "No authorised accounts; falling back to all-accounts flow.")
                    }

                    try {
                        // Second attempt: GetGoogleIdOption with filterByAuthorizedAccounts = false
                        requestGoogleIdToken(
                            credentialManager = credentialManager,
                            activity = activity,
                            serverClientId = serverClientId,
                            filterByAuthorizedAccounts = false
                        )
                    } catch (_: GetCredentialCancellationException) {
                        Log.d(TAG, "User cancelled Google sign-in (all-accounts)")
                        scope.launch(Dispatchers.IO) {
                            dbManager.logErrorReport(
                                errorType = "SIGNIN_CANCELLED",
                                message = "User cancelled during all-accounts flow. ClientID: $obfuscatedClientId"
                            )
                        }
                        toastManager.showShort("Sign-in cancelled")
                        null
                    } catch (e2: NoCredentialException) {
                        // No Google account is set up on the device at all.
                        Log.e(TAG, "No Google credentials found on device", e2)
                        toastManager.showLong("No Google account found on this device. Go to Settings → Accounts and add a Google account first.")
                        null
                    } catch (e2: GetCredentialException) {
                        handleCredentialException(e2, "all-accounts")
                        null
                    }
                }
                e is GetCredentialCancellationException -> {
                    Log.d(TAG, "User cancelled Google sign-in (authorised)")
                    scope.launch(Dispatchers.IO) {
                        dbManager.logErrorReport(
                            errorType = "SIGNIN_CANCELLED",
                            message = "User cancelled during authorised-accounts flow. ClientID: $obfuscatedClientId"
                        )
                    }
                    toastManager.showShort("Sign-in cancelled")
                    null
                }
                e is GetCredentialException -> {
                    handleCredentialException(e, "authorised-accounts")
                    null
                }
                else -> throw e
            }
        }
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    private fun handleCredentialException(e: GetCredentialException, flow: String) {
        val message = e.message ?: "Unknown error"
        Log.e(TAG, "Google sign-in failed ($flow flow). Type: ${e::class.java.simpleName}, Message: $message", e)
        
        // Log to Firebase Database with Device Details (Non-blocking)
        scope.launch(Dispatchers.IO) {
            dbManager.logErrorReport(
                errorType = "GOOGLE_SIGNIN_ERROR",
                message = "Flow: $flow, Exception: ${e::class.java.simpleName}, Msg: $message",
                exception = e
            )
        }

        val userMessage = when {
            message.contains("NETWORK_ERROR", ignoreCase = true) || message.contains("net::ERR", ignoreCase = true) ->
                "Network error. Please check your connection and device clock settings."
            message.contains("16") -> 
                "Sign-in failed (Error 16). Please ensure your app is correctly registered in the Google Console."
            message.contains("10") ->
                "Sign-in failed (Error 10). Check SHA-1 and Client ID configuration."
            else -> "Google sign-in failed: $message"
        }
        toastManager.showLong(userMessage)
    }

    private suspend fun requestGoogleIdToken(
        credentialManager: CredentialManager,
        activity: Activity,
        serverClientId: String,
        filterByAuthorizedAccounts: Boolean
    ): String? {
        if (serverClientId.isBlank()) {
            Log.e(TAG, "Google Server Client ID is empty.")
            return null
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(filterByAuthorizedAccounts) // Only auto-select if authorized
            .setNonce(generateNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(
            context = activity,
            request = request
        )
        return extractGoogleIdToken(result)
    }

    private fun extractGoogleIdToken(result: GetCredentialResponse): String? {
        val credential = result.credential
        Log.d(TAG, "Extracting token from credential type: ${credential.type}")

        return if (credential is CustomCredential &&
            (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
             credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
        ) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Log.d(TAG, "Successfully extracted ID token from GoogleIdTokenCredential")
                googleIdTokenCredential.idToken
            } catch (e: GoogleIdTokenParsingException) {
                Log.e(TAG, "Received an invalid Google ID token response", e)
                null
            }
        } else {
            Log.e(TAG, "Unexpected credential type: ${credential.type}. Data keys: ${credential.data.keySet()}")
            null
        }
    }

    private fun generateNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(
            randomBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── Other social providers ────────────────────────────────────────────────

    suspend fun signInWithFacebook(accessToken: String): FirebaseUser? {
        return try {
            Log.d(TAG, "Attempting Facebook sign-in")
            val credential = FacebookAuthProvider.getCredential(accessToken)
            signInWithCredential(credential, "Facebook")
        } catch (e: Exception) {
            Log.e(TAG, "Facebook credential creation failed", e)
            toastManager.showLong("Facebook sign-in failed: ${e.message}")
            null
        }
    }

    suspend fun signInWithTwitter(idToken: String, secret: String): FirebaseUser? {
        return try {
            Log.d(TAG, "Attempting Twitter sign-in")
            val credential = OAuthProvider.getCredential("twitter.com", idToken, secret)
            signInWithCredential(credential, "Twitter (X)")
        } catch (e: Exception) {
            Log.e(TAG, "Twitter credential creation failed", e)
            toastManager.showLong("Twitter sign-in failed: ${e.message}")
            null
        }
    }

    // ── Phone Auth ────────────────────────────────────────────────────────────

    fun verifyPhoneNumber(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        try {
            Log.d(TAG, "Attempting to verify phone number")
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            Log.e(TAG, "Phone number verification initialization failed", e)
            toastManager.showLong("Phone verification failed: ${e.message}")
        }
    }

    suspend fun signInWithPhoneCode(verificationId: String, smsCode: String): FirebaseUser? {
        return try {
            Log.d(TAG, "Attempting sign-in with phone code")
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
            signInWithCredential(credential, "Phone")
        } catch (e: Exception) {
            Log.e(TAG, "Phone credential creation failed", e)
            toastManager.showLong("Phone sign-in failed: ${e.message}")
            null
        }
    }

    // ── Sign-out ──────────────────────────────────────────────────────────────

    fun signOut() {
        try {
            Log.d(TAG, "Signing out user")
            auth.signOut()
            Log.d(TAG, "Sign-out successful")
            toastManager.showShort("Signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sign-out failed", e)
            toastManager.showLong("Sign-out failed: ${e.message}")
        }
    }

    suspend fun signOutWithCredentialClear(context: Context) {
        try {
            Log.d(TAG, "Clearing Credential Manager state")
            CredentialManager.create(context)
                .clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "clearCredentialState failed (non-fatal): ${e.message}")
        }
        signOut()
    }

    // ── Account management ────────────────────────────────────────────────────

    suspend fun sendPasswordResetEmail(email: String) {
        try {
            Log.d(TAG, "Attempting to send password reset email")
            auth.sendPasswordResetEmail(email).await()
            Log.d(TAG, "Password reset email sent")
            toastManager.showShort("Password reset email sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send password reset email", e)
            toastManager.showLong("Failed to send reset email: ${e.message}")
        }
    }

    suspend fun deleteUser() {
        try {
            val user = auth.currentUser
            Log.d(TAG, "Attempting to delete user account")
            user?.delete()?.await()
            Log.d(TAG, "User account deleted successfully")
            toastManager.showShort("User account deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user", e)
            toastManager.showLong("Failed to delete user: ${e.message}")
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun signInWithCredential(
        credential: AuthCredential,
        providerName: String
    ): FirebaseUser? {
        return try {
            Log.d(TAG, "Signing in with credential from provider: $providerName")
            val result = auth.signInWithCredential(credential).await()
            Log.d(TAG, "$providerName sign-in successful")
            toastManager.showShort("Signed in with $providerName")
            result.user
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "$providerName sign-in failed", e)
            toastManager.showLong("$providerName sign-in failed: ${e.localizedMessage}")
            null
        }
    }
}
