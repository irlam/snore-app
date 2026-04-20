package com.chrisirlam.snorenudge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrisirlam.snorenudge.data.AppSettings
import com.chrisirlam.snorenudge.data.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsDataStore(application)

    val settings: StateFlow<AppSettings> = store.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setSensitivity(v: Float) = viewModelScope.launch { store.setSensitivity(v) }
    fun setTriggerDuration(v: Int) = viewModelScope.launch { store.setTriggerDurationSeconds(v) }
    fun setCooldownDuration(v: Int) = viewModelScope.launch { store.setCooldownDurationSeconds(v) }
    fun setWatchVibration(v: Boolean) = viewModelScope.launch { store.setWatchVibrationEnabled(v) }
    fun setPhoneVibration(v: Boolean) = viewModelScope.launch { store.setPhoneVibrationEnabled(v) }
    fun setPhoneSound(v: Boolean) = viewModelScope.launch { store.setPhoneSoundEnabled(v) }
    fun setVibrationStrong(v: Boolean) = viewModelScope.launch { store.setVibrationStrong(v) }
    fun setDebugMode(v: Boolean) = viewModelScope.launch { store.setDebugMode(v) }
}
