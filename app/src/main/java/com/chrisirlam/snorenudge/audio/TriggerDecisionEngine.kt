package com.chrisirlam.snorenudge.audio

import android.util.Log
import com.chrisirlam.snorenudge.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

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
        val frameConfidence: Float,
        val rollingConfidence: Float,
        val shouldTrigger: Boolean,
        val cooldownRemainingMs: Long,
        val triggerThreshold: Float
    )

    private val longWindowSize = 120
    private val recentWindowSize = 24
    private val emaAlpha = 0.18f
    private val scoreWindow = ArrayDeque<Float>(longWindowSize)

    private var consecutivePositiveFrames = 0
    private var currentState = State.IDLE
    private var cooldownEndMs = 0L
    private var emaConfidence = 0f
    private var noiseFloorEma = 0f

    private val _stateFlow = MutableStateFlow(
        DecisionResult(State.IDLE, 0f, 0f, false, 0L, 0f)
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
        if (scoreWindow.size >= longWindowSize) scoreWindow.removeFirst()
        scoreWindow.addLast(score)
        emaConfidence = if (scoreWindow.size == 1) score else (emaConfidence + ((score - emaConfidence) * emaAlpha))
        val noiseAlpha = if (score <= 0.45f) 0.06f else 0.015f
        noiseFloorEma = if (scoreWindow.size == 1) score else (noiseFloorEma + ((score - noiseFloorEma) * noiseAlpha))

        val longAverage = scoreWindow.average().toFloat()
        val recentAverage = scoreWindow.takeLast(recentWindowSize).average().toFloat()
        val rollingConfidence = (
            (emaConfidence * 0.5f) +
                (recentAverage * 0.35f) +
                (longAverage * 0.15f)
            ).coerceIn(0f, 1f)

        val triggerThreshold = (0.62f - (settings.sensitivity * 0.22f)).coerceIn(0.36f, 0.62f)
        val dynamicNoiseGate = (noiseFloorEma + (0.26f - (settings.sensitivity * 0.12f))).coerceIn(0.30f, 0.88f)
        val effectiveTriggerThreshold = max(triggerThreshold, dynamicNoiseGate)
        val entryThreshold = max(
            triggerThreshold + 0.06f,
            effectiveTriggerThreshold + 0.04f
        ).coerceAtMost(0.95f)
        val releaseThreshold = (effectiveTriggerThreshold - 0.14f).coerceAtLeast(0.20f)
        val recentGate = (noiseFloorEma + 0.08f).coerceAtMost(0.90f)

        val framesRequired = ((settings.triggerDurationSeconds * 1000) / 50).coerceAtLeast(10)

        val result: DecisionResult = when (currentState) {
            State.COOLDOWN -> {
                if (nowMs >= cooldownEndMs) {
                    Log.d(TAG, "Cooldown ended")
                    currentState = State.IDLE
                    consecutivePositiveFrames = 0
                }
                DecisionResult(
                    currentState,
                    score,
                    rollingConfidence,
                    false,
                    (cooldownEndMs - nowMs).coerceAtLeast(0),
                    effectiveTriggerThreshold
                )
            }

            State.IDLE, State.ACCUMULATING -> {
                val positiveFrame =
                    (score >= effectiveTriggerThreshold || rollingConfidence >= entryThreshold) &&
                        recentAverage >= recentGate

                if (positiveFrame) {
                    consecutivePositiveFrames++
                    currentState = State.ACCUMULATING
                } else {
                    val decayStep = if (rollingConfidence <= releaseThreshold) 3 else 1
                    consecutivePositiveFrames = (consecutivePositiveFrames - decayStep).coerceAtLeast(0)
                    if (consecutivePositiveFrames == 0) currentState = State.IDLE
                }

                if (consecutivePositiveFrames >= framesRequired) {
                    Log.i(TAG, "Snore trigger! rollingConf=$rollingConfidence consecutive=$consecutivePositiveFrames")
                    currentState = State.TRIGGERED
                    cooldownEndMs = nowMs + (settings.cooldownDurationSeconds * 1000L)
                    DecisionResult(State.TRIGGERED, score, rollingConfidence, true, 0L, effectiveTriggerThreshold)
                        .also {
                            currentState = State.COOLDOWN
                            consecutivePositiveFrames = 0
                            scoreWindow.clear()
                            emaConfidence = 0f
                            noiseFloorEma = 0f
                        }
                } else {
                    DecisionResult(currentState, score, rollingConfidence, false, 0L, effectiveTriggerThreshold)
                }
            }

            State.TRIGGERED -> {
                currentState = State.COOLDOWN
                DecisionResult(
                    State.COOLDOWN,
                    score,
                    rollingConfidence,
                    false,
                    (cooldownEndMs - nowMs).coerceAtLeast(0),
                    effectiveTriggerThreshold
                )
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
        emaConfidence = 0f
        noiseFloorEma = 0f
        _stateFlow.value = DecisionResult(State.IDLE, 0f, 0f, false, 0L, 0f)
    }

    val currentRollingConfidence: Float
        get() = if (scoreWindow.isEmpty()) 0f else scoreWindow.average().toFloat()
}
