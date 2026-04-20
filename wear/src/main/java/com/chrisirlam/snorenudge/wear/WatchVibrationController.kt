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
        /** Strong repeating pattern: [delay, on, off, on, off, on] */
        private val PATTERN_STRONG = longArrayOf(0, 500, 200, 500, 200, 500)
        private val AMPLITUDE_STRONG = intArrayOf(0, 255, 0, 255, 0, 255)

        /** Short single pulse pattern */
        private val PATTERN_SHORT = longArrayOf(0, 400)
        private val AMPLITUDE_SHORT = intArrayOf(0, 255)

        /** Test pattern — less intense */
        private val PATTERN_TEST = longArrayOf(0, 200, 150, 200)
        private val AMPLITUDE_TEST = intArrayOf(0, 200, 0, 200)
    }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /** Vibrate with the strong repeating pattern. Intended for snore nudge. */
    fun vibrateStrong() {
        Log.d(TAG, "vibrateStrong()")
        vibrate(PATTERN_STRONG, AMPLITUDE_STRONG)
    }

    /** Vibrate with a single short pulse. */
    fun vibrateShort() {
        Log.d(TAG, "vibrateShort()")
        vibrate(PATTERN_SHORT, AMPLITUDE_SHORT)
    }

    /** Non-intrusive test vibration. */
    fun vibrateTest() {
        Log.d(TAG, "vibrateTest()")
        vibrate(PATTERN_TEST, AMPLITUDE_TEST)
    }

    fun cancel() {
        vibrator.cancel()
        Log.d(TAG, "Vibration cancelled")
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "No vibrator on this device")
            return
        }
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        vibrator.vibrate(effect)
    }
}
