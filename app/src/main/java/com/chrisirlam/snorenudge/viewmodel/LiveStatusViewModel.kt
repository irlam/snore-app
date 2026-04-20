package com.chrisirlam.snorenudge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisirlam.snorenudge.audio.TriggerDecisionEngine
import com.chrisirlam.snorenudge.service.ServiceBridge
import com.chrisirlam.snorenudge.service.SnoreMonitoringService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LiveStatusUiState(
    val frameConfidence: Float = 0f,
    val rollingConfidence: Float = 0f,
    val triggerThreshold: Float = 0f,
    val engineState: TriggerDecisionEngine.State = TriggerDecisionEngine.State.IDLE,
    val lastTriggerTime: String = "None",
    val cooldownRemainingMs: Long = 0L,
    val isMonitoring: Boolean = false,
    val rmsLevel: Float = 0f,
    val zeroCrossingRate: Float = 0f,
    val lowFrequencyRatio: Float = 0f
)

class LiveStatusViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LiveStatusUiState())
    val uiState: StateFlow<LiveStatusUiState> = _uiState.asStateFlow()

    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    init {
        // Collect live detection state from the service bridge, throttled to 250 ms.
        viewModelScope.launch {
            ServiceBridge.liveState
                .sample(250L)
                .collect { live ->
                    val lastTriggerStr = if (live.lastTriggerMs != null)
                        formatter.format(Instant.ofEpochMilli(live.lastTriggerMs))
                    else "None"
                    _uiState.update {
                        it.copy(
                            frameConfidence = live.frameConfidence,
                            rollingConfidence = live.rollingConfidence,
                            triggerThreshold = live.triggerThreshold,
                            engineState = live.engineState,
                            cooldownRemainingMs = live.cooldownRemainingMs,
                            lastTriggerTime = lastTriggerStr,
                            rmsLevel = live.rmsLevel,
                            zeroCrossingRate = live.zeroCrossingRate,
                            lowFrequencyRatio = live.lowFrequencyRatio
                        )
                    }
                }
        }

        // Poll service running state every 250 ms (service bridge resets on stop,
        // but isRunning gives a reliable indicator)
        viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(isMonitoring = SnoreMonitoringService.isRunning) }
                delay(250)
            }
        }
    }

    /**
     * Called by the debug screen or external code to push a status update manually.
     * Normally the service updates via [ServiceBridge] directly.
     */
    fun updateLiveStatus(
        confidence: Float,
        state: TriggerDecisionEngine.State,
        lastTriggerMs: Long?,
        cooldownRemainingMs: Long
    ) {
        val lastTriggerStr = if (lastTriggerMs != null)
            formatter.format(Instant.ofEpochMilli(lastTriggerMs))
        else "None"

        _uiState.update {
            it.copy(
                frameConfidence = confidence,
                rollingConfidence = confidence,
                engineState = state,
                lastTriggerTime = lastTriggerStr,
                cooldownRemainingMs = cooldownRemainingMs
            )
        }
    }
}
