package com.chrisirlam.snorenudge.service

import com.chrisirlam.snorenudge.audio.TriggerDecisionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton that bridges live detection state from [SnoreMonitoringService]
 * to the UI layer (ViewModels) without requiring service binding.
 *
 * [SnoreMonitoringService] writes here on every processed frame.
 * ViewModels observe [liveState] via [kotlinx.coroutines.flow.sample] to throttle UI updates.
 */
object ServiceBridge {

    data class LiveState(
        val rollingConfidence: Float = 0f,
        val engineState: TriggerDecisionEngine.State = TriggerDecisionEngine.State.IDLE,
        val cooldownRemainingMs: Long = 0L,
        val lastTriggerMs: Long? = null,
        val rmsLevel: Float = 0f,
        val zeroCrossingRate: Float = 0f
    )

    private val _liveState = MutableStateFlow(LiveState())
    val liveState: StateFlow<LiveState> = _liveState.asStateFlow()

    fun update(state: LiveState) {
        _liveState.value = state
    }

    fun reset() {
        _liveState.value = LiveState()
    }
}
