package com.rp.dedup.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AppNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_URGENT_ID = "urgent_notifications"
        const val CHANNEL_DEFAULT_ID = "default_notifications"
        
        const val ACTION_POSITIVE = "com.rp.dedup.ACTION_POSITIVE"
        const val ACTION_NEGATIVE = "com.rp.dedup.ACTION_NEGATIVE"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val urgentChannel = NotificationChannel(
                CHANNEL_URGENT_ID,
                "Urgent Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Used for critical app alerts and urgent tasks"
            }

            val defaultChannel = NotificationChannel(
                CHANNEL_DEFAULT_ID,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Standard app notifications and updates"
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(urgentChannel)
            manager.createNotificationChannel(defaultChannel)
        }
    }

    /**
     * Checks if the app has permission to post notifications.
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission is granted by default on older versions
            true
        }
    }

    /**
     * Shows a customizable notification with two action buttons.
     *
     * @param id Unique ID for the notification
     * @param title Notification title
     * @param description Notification body text
     * @param isUrgent Whether to use the High Importance channel
     * @param positiveLabel Text for the positive action button
     * @param negativeLabel Text for the negative action button
     */
    fun showActionNotification(
        id: Int,
        title: String,
        description: String,
        isUrgent: Boolean = false,
        positiveLabel: String = "Accept",
        negativeLabel: String = "Decline"
    ) {
        if (!hasNotificationPermission()) return

        val channelId = if (isUrgent) CHANNEL_URGENT_ID else CHANNEL_DEFAULT_ID

        // Create Intents for actions (to be handled by a BroadcastReceiver or Activity)
        val positiveIntent = Intent(ACTION_POSITIVE).apply {
            putExtra(EXTRA_NOTIFICATION_ID, id)
            setPackage(context.packageName)
        }
        val negativeIntent = Intent(ACTION_NEGATIVE).apply {
            putExtra(EXTRA_NOTIFICATION_ID, id)
            setPackage(context.packageName)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val positivePendingIntent = PendingIntent.getBroadcast(context, id * 2, positiveIntent, flags)
        val negativePendingIntent = PendingIntent.getBroadcast(context, id * 2 + 1, negativeIntent, flags)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder icon
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(if (isUrgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(0, positiveLabel, positivePendingIntent)
            .addAction(0, negativeLabel, negativePendingIntent)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(id, builder.build())
            } catch (e: SecurityException) {
                // Should be caught by hasNotificationPermission() check, but safety first
            }
        }
    }

    fun cancelNotification(id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}
