package com.rp.dedup.core.firebase.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rp.dedup.core.common.Constants
import com.rp.dedup.core.notifications.AppNotificationManager
import java.util.concurrent.atomic.AtomicInteger

class FirebaseMessageService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FirebaseMessageService"
        private val notifIdCounter = AtomicInteger(0)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "From: ${message.from}")

        // Check if message contains a notification payload.
        message.notification?.let {
            val title = it.title ?: "DeDup"
            val body = it.body ?: Constants.EMPTY_STRING
            
            // Show notification using our app manager
            val notificationManager = AppNotificationManager(applicationContext)
            notificationManager.showSimpleNotification(
                id = notifIdCounter.getAndIncrement(),
                title = title,
                message = body
            )
        }

        // Check if message contains a data payload.
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${message.data}")
            // Optional: Handle data payload (e.g. trigger a scan background)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        // Optional: Upload token to your server or Firebase Database
    }
}
