package com.chrisirlam.snorenudge.viewmodel

import android.app.Application
import android.os.PowerManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisirlam.snorenudge.service.SnoreMonitoringService
import com.chrisirlam.snorenudge.watch.WatchCommandSender
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val isMonitoring: Boolean = false,
    val watchConnected: Boolean = false,
    val watchNodeCount: Int = 0,
    val hasMicPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val batteryOptimisationIgnored: Boolean = false,
    val isSamsungDevice: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val watchCommandSender = WatchCommandSender(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        updateDeviceState()
        pollStatus()
    }

    private fun updateDeviceState() {
        val ctx = getApplication<Application>()
        val mic = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
        val powerManager = ctx.getSystemService(PowerManager::class.java)
        val batteryOptimisationIgnored = powerManager?.isIgnoringBatteryOptimizations(ctx.packageName) == true

        _uiState.update { it.copy(
            hasMicPermission = mic,
            hasNotificationPermission = notif,
            isMonitoring = SnoreMonitoringService.isRunning,
            batteryOptimisationIgnored = batteryOptimisationIgnored,
            isSamsungDevice = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        ) }
    }

    fun onPermissionsUpdated() = updateDeviceState()

    fun startMonitoring() {
        val state = _uiState.value
        if (!state.hasMicPermission) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.hasNotificationPermission) return

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
        watchCommandSender.sendVibrateCommand(strong = true)
    }

    fun fireFakeSnore() {
        val ctx = getApplication<Application>()
        SnoreMonitoringService.sendFakeTrigger(ctx)
    }

    private fun pollStatus() = viewModelScope.launch {
        while (currentCoroutineContext().isActive) {
            val count = watchCommandSender.getConnectedNodeCount()
            updateDeviceState()
            _uiState.update { it.copy(watchConnected = count > 0, watchNodeCount = count) }
            delay(10_000L)
        }
    }
}
