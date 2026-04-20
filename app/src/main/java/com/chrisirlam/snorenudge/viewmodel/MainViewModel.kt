package com.chrisirlam.snorenudge.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisirlam.snorenudge.audio.TriggerDecisionEngine
import com.chrisirlam.snorenudge.data.SettingsDataStore
import com.chrisirlam.snorenudge.service.SnoreMonitoringService
import com.chrisirlam.snorenudge.watch.WatchCommandSender
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val isMonitoring: Boolean = false,
    val watchConnected: Boolean = false,
    val watchNodeCount: Int = 0,
    val hasMicPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val watchCommandSender = WatchCommandSender(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkPermissions()
        pollWatchConnection()
    }

    private fun checkPermissions() {
        val ctx = getApplication<Application>()
        val mic = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        _uiState.update { it.copy(
            hasMicPermission = mic,
            hasNotificationPermission = notif,
            isMonitoring = SnoreMonitoringService.isRunning
        ) }
    }

    fun onPermissionsUpdated() = checkPermissions()

    fun startMonitoring() {
        val ctx = getApplication<Application>()
        SnoreMonitoringService.startMonitoring(ctx)
        _uiState.update { it.copy(isMonitoring = true) }
    }

    fun stopMonitoring() {
        val ctx = getApplication<Application>()
        SnoreMonitoringService.stopMonitoring(ctx)
        _uiState.update { it.copy(isMonitoring = false) }
    }

    fun sendTestVibrate() = viewModelScope.launch {
        watchCommandSender.sendTestVibrateCommand()
    }

    fun fireFakeSnore() {
        val ctx = getApplication<Application>()
        val intent = android.content.Intent(ctx, SnoreMonitoringService::class.java)
            .setAction(SnoreMonitoringService.ACTION_FAKE_SNORE)
        ctx.startService(intent)
    }

    private fun pollWatchConnection() = viewModelScope.launch {
        while (true) {
            val count = watchCommandSender.getConnectedNodeCount()
            _uiState.update { it.copy(watchConnected = count > 0, watchNodeCount = count) }
            delay(10_000L) // poll every 10 s
        }
    }
}
