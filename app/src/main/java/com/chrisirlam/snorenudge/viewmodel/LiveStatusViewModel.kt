package com.chrisirlam.snorenudge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisirlam.snorenudge.audio.TriggerDecisionEngine
import com.chrisirlam.snorenudge.data.AppSettings
import com.chrisirlam.snorenudge.data.SettingsDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LiveStatusUiState(
    val rollingConfidence: Float = 0f,
    val engineState: TriggerDecisionEngine.State = TriggerDecisionEngine.State.IDLE,
    val lastTriggerTime: String = "None",
    val cooldownRemainingMs: Long = 0L,
    val isMonitoring: Boolean = false
)

class LiveStatusViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)

    private val _uiState = MutableStateFlow(LiveStatusUiState())
    val uiState: StateFlow<LiveStatusUiState> = _uiState.asStateFlow()

    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    init {
        // Poll the global service state every 250 ms
        viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(isMonitoring = com.chrisirlam.snorenudge.service.SnoreMonitoringService.isRunning) }
                delay(250)
            }
        }
    }

    /**
     * Called by the service (or debug screen) to push a live status update.
     * In a production app this would be done via a shared StateFlow in a
     * service-bound ViewModel or via a broadcast.
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
                rollingConfidence = confidence,
                engineState = state,
                lastTriggerTime = lastTriggerStr,
                cooldownRemainingMs = cooldownRemainingMs
            )
        }
    }
}
