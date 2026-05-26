package com.rp.dedup.core.firebase.db

import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.FirebaseDatabase
import com.rp.dedup.BuildConfig
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseDbManager {
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val crashlytics = FirebaseCrashlytics.getInstance()

    companion object {
        private const val TAG = "FirebaseDbManager"
    }

    suspend fun logErrorReport(errorType: String, message: String, exception: Throwable? = null): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: "anonymous"
            val timestamp = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = sdf.format(Date(timestamp))

            // Gather Device Details
            val deviceDetails = mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "device" to Build.DEVICE,
                "brand" to Build.BRAND,
                "androidVersion" to Build.VERSION.RELEASE,
                "sdkInt" to Build.VERSION.SDK_INT,
                "securityPatch" to Build.VERSION.SECURITY_PATCH,
                "appVersion" to BuildConfig.VERSION_NAME,
                "appVersionCode" to BuildConfig.VERSION_CODE,
            )

            val reportMap = mapOf(
                "errorType" to errorType,
                "message" to message,
                "userId" to userId,
                "timestamp" to timestamp,
                "dateTime" to formattedDate,
                "device" to deviceDetails,
                "exceptionClass" to (exception?.javaClass?.simpleName ?: "none")
            )

            // Log to Firebase Realtime Database
            Log.d(TAG, "Logging error report to database: $errorType")
            database.getReference("error_reports")
                .push()
                .setValue(reportMap)
                .await()

            // Also log to Crashlytics as a non-fatal or custom log
            crashlytics.setCustomKey("last_error_type", errorType)
            crashlytics.setCustomKey("device_model", Build.MODEL)
            crashlytics.log("Custom Error Report: $errorType - $message")
            exception?.let { crashlytics.recordException(it) }

            Log.d(TAG, "Successfully logged error report")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging error report", e)
            Result.failure(e)
        }
    }

    suspend fun submitFeedback(type: String, content: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: "anonymous"
            val timestamp = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = sdf.format(Date(timestamp))

            val feedbackMap = mapOf(
                "type" to type,
                "content" to content,
                "userId" to userId,
                "timestamp" to timestamp,
                "dateTime" to formattedDate
            )

            val ref = when (type) {
                "FEEDBACK" -> database.getReference("feedback")
                "FEATURE_REQUEST" -> database.getReference("feature_requests")
                else -> database.getReference("misc_feedback")
            }

            Log.d(TAG, "Attempting to submit $type")
            ref.push().setValue(feedbackMap).await()
            Log.d(TAG, "Successfully submitted $type")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting $type", e)
            Result.failure(e)
        }
    }
}
