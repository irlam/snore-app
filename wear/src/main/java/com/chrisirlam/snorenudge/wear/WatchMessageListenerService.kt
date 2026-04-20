package com.chrisirlam.snorenudge.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chrisirlam.snorenudge.shared.WearProtocol
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "WatchMessageListener"
private const val CHANNEL_ID = "snore_nudge_watch"
private const val NOTIFICATION_ID = 2001

/** Shared state that the WatchMainActivity/ViewModel can observe. */
object WatchState {
    val lastCommandFlow = MutableStateFlow<WatchCommand?>(null)
}

enum class WatchCommand { VIBRATE_STRONG, VIBRATE_MEDIUM, TEST, STOP }

/**
 * Receives messages from the paired phone and triggers vibration.
 *
 * This is a [WearableListenerService] so it is woken up even when the watch
 * activity is not in the foreground — critical for overnight use.
 *
 * On receiving a snore nudge:
 *  1. Acquires a wake lock to keep the watch CPU alive.
 *  2. Triggers repeating vibration via [WatchVibrationController].
 *  3. Posts a high-priority notification.
 *  4. Launches [WatchMainActivity] to show the "Roll Over!" screen.
 */
class WatchMessageListenerService : WearableListenerService() {

    private lateinit var vibrationController: WatchVibrationController
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        vibrationController = WatchVibrationController(this)
        createNotificationChannel()
    }

    override fun onMessageReceived(event: MessageEvent) {
        val payload = String(event.data)
        Log.d(TAG, "Message received: path=${event.path} payload=$payload")

        acquireWakeLock()
        try {
            val command = when (event.path) {
                WearProtocol.Paths.VIBRATE -> {
                    if (payload == WearProtocol.Payload.STRONG) WatchCommand.VIBRATE_STRONG else WatchCommand.VIBRATE_MEDIUM
                }
                WearProtocol.Paths.TEST_VIBRATE -> WatchCommand.TEST
                WearProtocol.Paths.STOP_VIBRATE -> WatchCommand.STOP
                else -> null
            }

            command?.let { cmd ->
                WatchState.lastCommandFlow.value = cmd
                handleCommand(cmd)
            }
        } finally {
            releaseWakeLock()
        }
    }

    override fun onDestroy() {
        vibrationController.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun handleCommand(command: WatchCommand) {
        when (command) {
            WatchCommand.VIBRATE_STRONG -> {
                vibrationController.vibrateStrong()
                showNudgeNotification("Snore detected — roll over!")
                launchWatchActivity()
            }
            WatchCommand.VIBRATE_MEDIUM -> {
                vibrationController.vibrateShort()
                showNudgeNotification("Snore detected!")
                launchWatchActivity()
            }
            WatchCommand.TEST -> {
                vibrationController.vibrateTest()
                Log.d(TAG, "Test vibration triggered")
            }
            WatchCommand.STOP -> vibrationController.cancel()
        }
    }

    /** Bring the watch UI to the foreground so the user sees the nudge. */
    private fun launchWatchActivity() {
        val intent = Intent(this, WatchMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch WatchMainActivity: ${e.message}")
        }
    }

    private fun showNudgeNotification(text: String) {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, WatchMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("SnoreNudge")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notif)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Snore Nudge Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts from SnoreNudge phone app"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG:WearWakeLock"
        ).apply { acquire(10_000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
