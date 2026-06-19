package com.rp.dedup.core.handoff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rp.dedup.MainActivity

/**
 * Internal-only receiver for in-process deep-link navigation.
 * Declared exported=false in the manifest — only this app can send it.
 */
class HandoffReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NAVIGATE = "com.rp.dedup.action.NAVIGATE"
        const val EXTRA_ROUTE = "target_route"

        fun buildIntent(context: Context, route: String): Intent =
            Intent(ACTION_NAVIGATE).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_ROUTE, route)
            }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_NAVIGATE) return
        val route = intent.getStringExtra(EXTRA_ROUTE) ?: return
        Log.d("HandoffReceiver", "Internal navigate to $route")
        val startIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        context.startActivity(startIntent)
    }
}
