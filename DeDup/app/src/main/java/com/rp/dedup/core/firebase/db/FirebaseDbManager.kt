package com.rp.dedup.core.firebase.db

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseDbManager {
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirebaseDbManager"
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
