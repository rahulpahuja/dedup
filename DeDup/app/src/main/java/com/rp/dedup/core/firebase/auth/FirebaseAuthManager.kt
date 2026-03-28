package com.rp.dedup.core.firebase.auth

import android.app.Activity
import android.util.Log
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
import java.util.concurrent.TimeUnit

class FirebaseAuthManager(
    private val toastManager: ToastManager,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    companion object {
        private const val TAG = "FirebaseAuthManager"
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

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

    suspend fun signInWithGoogle(idToken: String): FirebaseUser? {
        return try {
            Log.d(TAG, "Attempting Google sign-in")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            signInWithCredential(credential, "Google")
        } catch (e: Exception) {
            Log.e(TAG, "Google credential creation failed", e)
            toastManager.showLong("Google sign-in failed: ${e.message}")
            null
        }
    }

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
                .setPhoneNumber(phoneNumber)       // Phone number to verify
                .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
                .setActivity(activity)             // Activity (for callback binding)
                .setCallbacks(callbacks)           // OnVerificationStateChangedCallbacks
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

    private suspend fun signInWithCredential(credential: AuthCredential, providerName: String): FirebaseUser? {
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
}
