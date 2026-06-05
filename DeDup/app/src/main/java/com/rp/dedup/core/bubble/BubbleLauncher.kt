package com.rp.dedup.core.bubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.rp.dedup.R

object BubbleLauncher {
    private const val CHANNEL_ID = "dedup_bubble_scanner"
    private const val NOTIFICATION_ID = 9001

    @RequiresApi(Build.VERSION_CODES.R)
    fun launch(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel(CHANNEL_ID, "Scanner Bubble", NotificationManager.IMPORTANCE_HIGH)
                .also { notificationManager.createNotificationChannel(it) }
        }

        val bubbleIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, BubbleActivity::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bubbleMetadata = Notification.BubbleMetadata.Builder(
            bubbleIntent,
            Icon.createWithResource(context, R.mipmap.ic_launcher)
        )
            .setDesiredHeight(400)
            .setAutoExpandBubble(true)
            .setSuppressNotification(true)
            .build()

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DeDup Scanner")
            .setContentText("Scanning in progress...")
            .setBubbleMetadata(bubbleMetadata)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
