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
        /**
         * Strong repeating pattern: 500 ms on / 200 ms off, repeated until cancelled.
         * repeat = 1 means start repeating from index 1 of the timing array onwards.
         * The watch activity or user must call cancel() to stop.
         */
        private val PATTERN_STRONG = longArrayOf(0, 500, 200, 500, 200, 500)
        private val AMPLITUDE_STRONG = intArrayOf(0, 255, 0, 255, 0, 255)
        private const val REPEAT_STRONG = 1  // repeat from index 1 indefinitely

        /** Short single pulse pattern — one burst only */
        private val PATTERN_SHORT = longArrayOf(0, 400)
        private val AMPLITUDE_SHORT = intArrayOf(0, 255)
        private const val REPEAT_NONE = -1

        /** Test pattern — less intense, plays once */
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

    /** Vibrate with the strong repeating pattern. Repeats until [cancel] is called. */
    fun vibrateStrong() {
        Log.d(TAG, "vibrateStrong()")
        vibrate(PATTERN_STRONG, AMPLITUDE_STRONG, REPEAT_STRONG)
    }

    /** Vibrate with a single short pulse. */
    fun vibrateShort() {
        Log.d(TAG, "vibrateShort()")
        vibrate(PATTERN_SHORT, AMPLITUDE_SHORT, REPEAT_NONE)
    }

    /** Non-intrusive test vibration — plays once. */
    fun vibrateTest() {
        Log.d(TAG, "vibrateTest()")
        vibrate(PATTERN_TEST, AMPLITUDE_TEST, REPEAT_NONE)
    }

    fun cancel() {
        vibrator.cancel()
        Log.d(TAG, "Vibration cancelled")
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray, repeat: Int) {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "No vibrator on this device")
            return
        }
        val effect = VibrationEffect.createWaveform(timings, amplitudes, repeat)
        vibrator.vibrate(effect)
    }
}
