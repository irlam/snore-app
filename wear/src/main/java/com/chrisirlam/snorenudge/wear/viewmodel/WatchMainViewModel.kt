package com.chrisirlam.snorenudge.wear.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisirlam.snorenudge.wear.WatchCommand
import com.chrisirlam.snorenudge.wear.WatchState
import com.chrisirlam.snorenudge.wear.WatchVibrationController
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WatchUiState(
    val lastCommand: WatchCommand? = null,
    val lastCommandTime: String = "None",
    val isAwaitingCommand: Boolean = true
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
    }

    fun testVibration() = vibrationController.vibrateTest()
    fun stopVibration() = vibrationController.cancel()
}
