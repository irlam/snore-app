package com.chrisirlam.snorenudge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "BootReceiver"

/**
 * Receives BOOT_COMPLETED so the app can restart monitoring if the user had
 * it running before the device rebooted.
 *
 * Currently a stub — to enable auto-restart, persist a "was running" flag in
 * DataStore and check it here.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed received — auto-restart not enabled by default")
            // To enable: read DataStore setting and call SnoreMonitoringService.startMonitoring(context)
        }
    }
}
