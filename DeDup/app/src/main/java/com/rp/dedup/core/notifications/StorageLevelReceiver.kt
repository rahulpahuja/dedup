package com.rp.dedup.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.util.Log

/**
 * Receiver that listens for low storage events and alerts the user
 * if storage usage exceeds 90%.
 */
class StorageLevelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // We listen for the system's low storage broadcast as a trigger
        if (intent.action == Intent.ACTION_DEVICE_STORAGE_LOW) {
            checkStorageAndNotify(context)
        }
    }

    private fun checkStorageAndNotify(context: Context) {
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val usedBytes = totalBytes - availableBytes
            
            val usedPercentage = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes.toDouble()) * 100 else 0.0

            Log.d("StorageLevelReceiver", "Storage check: ${usedPercentage.toInt()}% used")

            if (usedPercentage >= 90.0) {
                val notificationManager = AppNotificationManager(context)
                notificationManager.showActionNotification(
                    id = 1001,
                    title = "Storage Almost Full",
                    description = "Your device is using ${usedPercentage.toInt()}% of storage. Clean up duplicates to free up space!",
                    isUrgent = true,
                    positiveLabel = "Clean Now",
                    negativeLabel = "Dismiss"
                )
            }
        } catch (e: Exception) {
            Log.e("StorageLevelReceiver", "Failed to check storage level", e)
        }
    }
}
