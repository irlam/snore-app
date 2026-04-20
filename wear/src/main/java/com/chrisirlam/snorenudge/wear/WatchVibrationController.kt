package com.chrisirlam.snorenudge.wear

import android.content.Context
import android.os.*
import android.util.Log

private const val TAG = "WatchVibrationController"

/**
 * Controls vibration on the Samsung Galaxy Watch Ultra (Wear OS).
 *
 * Uses [VibrationEffect] for Android 8+ devices. The Galaxy Watch Ultra
 * supports [VibrationEffect.createWaveform] and can deliver stronger patterns
 * than simple one-shots.
 *
 * Pattern design intent:
 *   - SHORT: single 400 ms burst — subtle initial alert
 *   - STRONG: 3× 500 ms on / 200 ms off at full amplitude — hard to sleep through
 *   - TEST: 2× 200 ms at 80% amplitude — non-intrusive test
 */
class WatchVibrationController(private val context: Context) {

    companion object {
        private val PATTERN_STRONG = longArrayOf(0, 700, 120, 700, 120, 1000, 180, 1000)
        private val AMPLITUDE_STRONG = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)
        private const val REPEAT_STRONG = 1
        private const val STRONG_AUTO_CANCEL_MS = 12_000L

        private val PATTERN_SHORT = longArrayOf(0, 250, 120, 250, 120, 400)
        private val AMPLITUDE_SHORT = intArrayOf(0, 220, 0, 220, 0, 255)
        private const val REPEAT_NONE = -1

        private val PATTERN_TEST = longArrayOf(0, 200, 150, 200)
        private val AMPLITUDE_TEST = intArrayOf(0, 200, 0, 200)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var autoCancelRunnable: Runnable? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun vibrateStrong() {
        Log.d(TAG, "vibrateStrong()")
        scheduleAutoCancel(STRONG_AUTO_CANCEL_MS)
        vibrate(PATTERN_STRONG, AMPLITUDE_STRONG, REPEAT_STRONG)
    }

    fun vibrateShort() {
        Log.d(TAG, "vibrateShort()")
        vibrate(PATTERN_SHORT, AMPLITUDE_SHORT, REPEAT_NONE)
    }

    fun vibrateTest() {
        Log.d(TAG, "vibrateTest()")
        vibrate(PATTERN_TEST, AMPLITUDE_TEST, REPEAT_NONE)
    }

    fun cancel() {
        autoCancelRunnable?.let(handler::removeCallbacks)
        autoCancelRunnable = null
        vibrator.cancel()
        Log.d(TAG, "Vibration cancelled")
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray, repeat: Int) {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "No vibrator on this device")
            return
        }
        vibrator.cancel()
        val effect = VibrationEffect.createWaveform(timings, amplitudes, repeat)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrator.vibrate(effect)
        }
    }

    private fun scheduleAutoCancel(delayMs: Long) {
        autoCancelRunnable?.let(handler::removeCallbacks)
        autoCancelRunnable = Runnable { cancel() }
        handler.postDelayed(autoCancelRunnable!!, delayMs)
    }
}
