package com.chrisirlam.snorenudge.wear.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisirlam.snorenudge.wear.WatchCommand
import com.chrisirlam.snorenudge.wear.WatchState
import com.chrisirlam.snorenudge.wear.WatchVibrationController
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WatchUiState(
    val lastCommand: WatchCommand? = null,
    val lastCommandTime: String = "None",
    val isAwaitingCommand: Boolean = true,
    val isPhoneConnected: Boolean = false,
    val connectedPhoneCount: Int = 0
)

class WatchMainViewModel(application: Application) : AndroidViewModel(application) {

    val vibrationController = WatchVibrationController(application)
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    private val _uiState = MutableStateFlow(WatchUiState())
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            WatchState.lastCommandFlow.collect { cmd ->
                if (cmd != null) {
                    _uiState.update {
                        it.copy(
                            lastCommand = cmd,
                            lastCommandTime = formatter.format(Instant.now()),
                            isAwaitingCommand = false
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                val count = runCatching {
                    Wearable.getNodeClient(getApplication()).connectedNodes.await().size
                }.getOrDefault(0)
                _uiState.update {
                    it.copy(
                        isPhoneConnected = count > 0,
                        connectedPhoneCount = count
                    )
                }
                delay(5_000L)
            }
        }
    }

    fun testVibration() = vibrationController.vibrateTest()
    fun stopVibration() = vibrationController.cancel()
}
