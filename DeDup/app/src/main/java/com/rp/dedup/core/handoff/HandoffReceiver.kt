package com.rp.dedup.core.handoff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rp.dedup.MainActivity

/**
 * Handles incoming activity handoff requests from other devices (Android 17 API).
 */
class HandoffReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.HANDOFF_RECEIVED") {
            val route = intent.getStringExtra("handoff_route") ?: "dashboard"
            val scanType = intent.getStringExtra("scan_type")
            
            Log.d("HandoffReceiver", "Received handoff for $scanType, routing to $route")

            // Restart MainActivity with the handoff state
            val startIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("target_route", route)
                putExtra("handoff_scan_type", scanType)
            }
            context.startActivity(startIntent)
        }
    }
}
