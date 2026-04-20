package com.chrisirlam.snorenudge.audio

import android.util.Log
import com.chrisirlam.snorenudge.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "TriggerDecisionEngine"

/**
 * Aggregates per-frame classifier scores into a rolling confidence window
 * and decides when a snore event is confirmed enough to trigger a nudge.
 *
 * State machine:
 *   IDLE → ACCUMULATING → TRIGGERED → COOLDOWN → IDLE
 */
class TriggerDecisionEngine {

    enum class State { IDLE, ACCUMULATING, TRIGGERED, COOLDOWN }

    data class DecisionResult(
        val state: State,
        val rollingConfidence: Float,
        val shouldTrigger: Boolean,
        val cooldownRemainingMs: Long
    )

    // Rolling window of the last N frame scores (approx. 5 s at 50 ms/frame = 100 frames)
    private val windowSize = 100
    private val scoreWindow = ArrayDeque<Float>(windowSize)

    private var consecutivePositiveFrames = 0
    private var currentState = State.IDLE
    private var cooldownEndMs = 0L

    private val _stateFlow = MutableStateFlow(
        DecisionResult(State.IDLE, 0f, false, 0L)
    )
    val stateFlow: StateFlow<DecisionResult> = _stateFlow.asStateFlow()

    /**
     * Feed the score for the latest frame and return a [DecisionResult].
     *
     * @param score          Classifier output for this frame [0, 1]
     * @param settings       Current user settings
     * @param nowMs          Current time in milliseconds (injectable for testing)
     */
    fun onFrameScore(score: Float, settings: AppSettings, nowMs: Long = System.currentTimeMillis()): DecisionResult {
        // Maintain rolling window
        if (scoreWindow.size >= windowSize) scoreWindow.removeFirst()
        scoreWindow.addLast(score)
        val rollingConfidence = scoreWindow.average().toFloat()

        // Confidence threshold derived from sensitivity (higher sensitivity = lower required confidence)
        val confidenceThreshold = 0.55f - (settings.sensitivity - 0.5f) * 0.3f

        // Minimum number of consecutive positive frames before triggering
        // At 50 ms/frame: 10 = 500 ms, 20 = 1 s
        val framesRequired = ((settings.triggerDurationSeconds * 1000) / 50).coerceAtLeast(10)

        val result: DecisionResult = when (currentState) {
            State.COOLDOWN -> {
                if (nowMs >= cooldownEndMs) {
                    Log.d(TAG, "Cooldown ended")
                    currentState = State.IDLE
                    consecutivePositiveFrames = 0
                }
                DecisionResult(currentState, rollingConfidence, false, (cooldownEndMs - nowMs).coerceAtLeast(0))
            }

            State.IDLE, State.ACCUMULATING -> {
                if (score >= confidenceThreshold) {
                    consecutivePositiveFrames++
                    currentState = State.ACCUMULATING
                } else {
                    consecutivePositiveFrames = (consecutivePositiveFrames - 1).coerceAtLeast(0)
                    if (consecutivePositiveFrames == 0) currentState = State.IDLE
                }

                if (consecutivePositiveFrames >= framesRequired) {
                    Log.i(TAG, "Snore trigger! rollingConf=$rollingConfidence consecutive=$consecutivePositiveFrames")
                    currentState = State.TRIGGERED
                    cooldownEndMs = nowMs + (settings.cooldownDurationSeconds * 1000L)
                    DecisionResult(State.TRIGGERED, rollingConfidence, true, 0L)
                        .also {
                            // Immediately move to cooldown so we do not retrigger on next frame
                            currentState = State.COOLDOWN
                            consecutivePositiveFrames = 0
                        }
                } else {
                    DecisionResult(currentState, rollingConfidence, false, 0L)
                }
            }

            State.TRIGGERED -> {
                // Transient state — should not stay here
                currentState = State.COOLDOWN
                DecisionResult(State.COOLDOWN, rollingConfidence, false, (cooldownEndMs - nowMs).coerceAtLeast(0))
            }
        }

        _stateFlow.value = result
        return result
    }

    fun reset() {
        scoreWindow.clear()
        consecutivePositiveFrames = 0
        currentState = State.IDLE
        cooldownEndMs = 0L
        _stateFlow.value = DecisionResult(State.IDLE, 0f, false, 0L)
    }

    val currentRollingConfidence: Float
        get() = if (scoreWindow.isEmpty()) 0f else scoreWindow.average().toFloat()
}
