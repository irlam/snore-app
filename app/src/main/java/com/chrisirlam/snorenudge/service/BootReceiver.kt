package com.chrisirlam.snorenudge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "BootReceiver"

/**
 * Receives BOOT_COMPLETED and restarts [SnoreMonitoringService] if the user
 * had monitoring active before the device rebooted.
 *
 * The "was monitoring" flag is persisted synchronously via SharedPreferences
 * by [SnoreMonitoringService] whenever monitoring starts or stops.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (SnoreMonitoringService.wasMonitoring(context)) {
                Log.i(TAG, "Boot completed — restarting snore monitoring")
                SnoreMonitoringService.startMonitoring(context)
            } else {
                Log.d(TAG, "Boot completed — monitoring was not active, not restarting")
            }
        }
    }
}
