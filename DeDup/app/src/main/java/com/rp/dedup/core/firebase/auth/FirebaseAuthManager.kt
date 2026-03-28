package com.rp.dedup.core.firebase.auth

import android.app.Activity
import android.content.Context
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
import com.rp.dedup.core.notifications.ToastManager
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class FirebaseAuthManager(
    private val toastManager: ToastManager,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    companion object {
        private const val TAG = "FirebaseAuthManager"

        /**
         * Your OAuth 2.0 Web Client ID from the Google Cloud Console / Firebase Console.
         * Firebase Console → Authentication → Sign-in method → Google → Web SDK configuration.
         * TODO: Replace with your actual Web Client ID before shipping.
         */
        const val WEB_CLIENT_ID = "300582514488-0se0sjakfc04r7rfegpm8h0jnd7t7luh.apps.googleusercontent.com"
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
            Log.d(TAG, "Anonymous sign-in successful: ${result.user?.uid}")
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
            Log.d(TAG, "Attempting to create user with email: $email")
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Log.d(TAG, "User creation successful for: ${result.user?.email}")
            toastManager.showShort("User created: ${result.user?.email}")
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "User creation failed for $email", e)
            toastManager.showLong("Registration failed: ${e.message}")
            null
        }
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser? {
        return try {
            Log.d(TAG, "Attempting sign-in with email: $email")
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Log.d(TAG, "Sign-in successful for: ${result.user?.email}")
            toastManager.showShort("Signed in: ${result.user?.email}")
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed for $email", e)
            toastManager.showLong("Login failed: ${e.message}")
            null
        }
    }

    // ── Sign in with Google (Credential Manager) ──────────────────────────────

    /**
     * Launches the Credential Manager bottom-sheet to sign the user in with Google,
     * then authenticates with Firebase using the returned ID token.
     *
     * Flow:
     *  1. Try authorised accounts only (enables auto sign-in for returning users).
     *  2. If no authorised account exists → fall back to all accounts (sign-up flow).
     *  3. Extract the [GoogleIdTokenCredential] from the Credential Manager response.
     *  4. Exchange the ID token for a Firebase [FirebaseUser].
     *
     * @param activityContext Must be an [Activity] context, not an Application context,
     *   because Credential Manager needs to attach a bottom sheet to the window.
     */
    suspend fun signInWithGoogle(activityContext: Context): FirebaseUser? {
        return try {
            val idToken = fetchGoogleIdToken(activityContext) ?: return null
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            signInWithCredential(credential, "Google")
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            toastManager.showLong("Google sign-in failed: ${e.message}")
            null
        }
    }

    /**
     * Orchestrates the two-step Credential Manager request:
     *   Step 1 — Authorised accounts only   (bottom sheet + auto sign-in eligible).
     *   Step 2 — All device accounts        (shown when step 1 returns [NoCredentialException]).
     */
    private suspend fun fetchGoogleIdToken(activityContext: Context): String? {
        val credentialManager = CredentialManager.create(activityContext)

        // Step 1: previously-authorised accounts → enables Automatic Sign-in
        return try {
            requestGoogleIdToken(
                credentialManager = credentialManager,
                activityContext = activityContext,
                filterByAuthorizedAccounts = true,
                autoSelect = true
            )
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No authorised accounts; falling back to all-accounts flow")
            // Step 2: show all Google accounts on the device (new user / different account)
            try {
                requestGoogleIdToken(
                    credentialManager = credentialManager,
                    activityContext = activityContext,
                    filterByAuthorizedAccounts = false,
                    autoSelect = false
                )
            } catch (e2: GetCredentialCancellationException) {
                Log.d(TAG, "User cancelled Google sign-in")
                toastManager.showShort("Sign-in cancelled")
                null
            } catch (e2: GetCredentialException) {
                Log.e(TAG, "Google sign-in failed (all-accounts flow)", e2)
                toastManager.showLong("Google sign-in failed: ${e2.message}")
                null
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled Google sign-in")
            toastManager.showShort("Sign-in cancelled")
            null
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google sign-in failed (authorised-accounts flow)", e)
            toastManager.showLong("Google sign-in failed: ${e.message}")
            null
        }
    }

    /**
     * Builds and fires a single Credential Manager [GetCredentialRequest], then
     * extracts the Google ID token from the response.
     */
    private suspend fun requestGoogleIdToken(
        credentialManager: CredentialManager,
        activityContext: Context,
        filterByAuthorizedAccounts: Boolean,
        autoSelect: Boolean
    ): String? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(autoSelect)
            .setNonce(generateNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(
            context = activityContext,
            request = request
        )
        return extractGoogleIdToken(result)
    }

    /**
     * Unpacks the [GetCredentialResponse] and returns the raw Google ID token string,
     * or `null` if the credential type is unrecognised.
     */
    private fun extractGoogleIdToken(result: GetCredentialResponse): String? {
        val credential = result.credential
        return if (
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } catch (e: GoogleIdTokenParsingException) {
                Log.e(TAG, "Received an invalid Google ID token response", e)
                null
            }
        } else {
            Log.e(TAG, "Unexpected credential type: ${credential.type}")
            null
        }
    }

    /**
     * Generates a cryptographically strong random nonce (Base64-URL-safe, no padding).
     * Sent with each sign-in request to prevent replay attacks.
     */
    private fun generateNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(
            randomBytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
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

    /**
     * Step 1 of Phone Auth: Verify the phone number and send OTP.
     */
    fun verifyPhoneNumber(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        try {
            Log.d(TAG, "Attempting to verify phone number: $phoneNumber")
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

    /**
     * Step 2 of Phone Auth: Sign in using the verification ID and SMS code.
     */
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

    /**
     * Signs the user out of Firebase.
     * For Google sign-in, prefer [signOutWithCredentialClear] to also notify
     * the Credential Manager so the account picker resets for the next sign-in.
     */
    fun signOut() {
        try {
            Log.d(TAG, "Signing out user: ${currentUser?.email ?: currentUser?.uid}")
            auth.signOut()
            Log.d(TAG, "Sign-out successful")
            toastManager.showShort("Signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Sign-out failed", e)
            toastManager.showLong("Sign-out failed: ${e.message}")
        }
    }

    /**
     * Signs the user out of Firebase AND clears the Credential Manager session.
     *
     * Clearing credential state is important for Google sign-in: without it the
     * Credential Manager may skip the account-picker and auto-select the same
     * account next time, making it impossible to switch accounts.
     *
     * @param context Any valid [Context] (Activity or Application both work here).
     */
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
            Log.d(TAG, "Attempting to send password reset email to: $email")
            auth.sendPasswordResetEmail(email).await()
            Log.d(TAG, "Password reset email sent to: $email")
            toastManager.showShort("Password reset email sent to $email")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send password reset email to $email", e)
            toastManager.showLong("Failed to send reset email: ${e.message}")
        }
    }

    suspend fun deleteUser() {
        try {
            val user = auth.currentUser
            Log.d(TAG, "Attempting to delete user: ${user?.email ?: user?.uid}")
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
            Log.d(TAG, "$providerName sign-in successful: ${result.user?.email ?: result.user?.uid}")
            toastManager.showShort("Signed in with $providerName")
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "$providerName sign-in failed", e)
            toastManager.showLong("$providerName sign-in failed: ${e.message}")
            null
        }
    }
}
