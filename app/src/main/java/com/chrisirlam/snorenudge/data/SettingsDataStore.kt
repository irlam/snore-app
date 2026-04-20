package com.chrisirlam.snorenudge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "snore_nudge_settings")

/**
 * Persists all user-configurable settings via Jetpack DataStore.
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        val KEY_SENSITIVITY = floatPreferencesKey("sensitivity")
        val KEY_TRIGGER_DURATION_S = intPreferencesKey("trigger_duration_s")
        val KEY_COOLDOWN_DURATION_S = intPreferencesKey("cooldown_duration_s")
        val KEY_WATCH_VIBRATION_ENABLED = booleanPreferencesKey("watch_vibration_enabled")
        val KEY_PHONE_VIBRATION_ENABLED = booleanPreferencesKey("phone_vibration_enabled")
        val KEY_PHONE_SOUND_ENABLED = booleanPreferencesKey("phone_sound_enabled")
        val KEY_VIBRATION_STRONG = booleanPreferencesKey("vibration_strong")
        val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")

        // Defaults
        const val DEFAULT_SENSITIVITY = 0.5f
        const val DEFAULT_TRIGGER_DURATION_S = 5
        const val DEFAULT_COOLDOWN_DURATION_S = 60
        const val DEFAULT_WATCH_VIBRATION_ENABLED = true
        const val DEFAULT_PHONE_VIBRATION_ENABLED = false
        const val DEFAULT_PHONE_SOUND_ENABLED = false
        const val DEFAULT_VIBRATION_STRONG = true
        const val DEFAULT_DEBUG_MODE = false
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            AppSettings(
                sensitivity = prefs[KEY_SENSITIVITY] ?: DEFAULT_SENSITIVITY,
                triggerDurationSeconds = prefs[KEY_TRIGGER_DURATION_S] ?: DEFAULT_TRIGGER_DURATION_S,
                cooldownDurationSeconds = prefs[KEY_COOLDOWN_DURATION_S] ?: DEFAULT_COOLDOWN_DURATION_S,
                watchVibrationEnabled = prefs[KEY_WATCH_VIBRATION_ENABLED] ?: DEFAULT_WATCH_VIBRATION_ENABLED,
                phoneVibrationEnabled = prefs[KEY_PHONE_VIBRATION_ENABLED] ?: DEFAULT_PHONE_VIBRATION_ENABLED,
                phoneSoundEnabled = prefs[KEY_PHONE_SOUND_ENABLED] ?: DEFAULT_PHONE_SOUND_ENABLED,
                vibrationStrong = prefs[KEY_VIBRATION_STRONG] ?: DEFAULT_VIBRATION_STRONG,
                debugMode = prefs[KEY_DEBUG_MODE] ?: DEFAULT_DEBUG_MODE
            )
        }

    suspend fun setSensitivity(value: Float) {
        context.dataStore.edit { it[KEY_SENSITIVITY] = value }
    }

    suspend fun setTriggerDurationSeconds(value: Int) {
        context.dataStore.edit { it[KEY_TRIGGER_DURATION_S] = value }
    }

    suspend fun setCooldownDurationSeconds(value: Int) {
        context.dataStore.edit { it[KEY_COOLDOWN_DURATION_S] = value }
    }

    suspend fun setWatchVibrationEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_WATCH_VIBRATION_ENABLED] = value }
    }

    suspend fun setPhoneVibrationEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_PHONE_VIBRATION_ENABLED] = value }
    }

    suspend fun setPhoneSoundEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_PHONE_SOUND_ENABLED] = value }
    }

    suspend fun setVibrationStrong(value: Boolean) {
        context.dataStore.edit { it[KEY_VIBRATION_STRONG] = value }
    }

    suspend fun setDebugMode(value: Boolean) {
        context.dataStore.edit { it[KEY_DEBUG_MODE] = value }
    }
}

/**
 * Immutable snapshot of all app settings.
 */
data class AppSettings(
    val sensitivity: Float = SettingsDataStore.DEFAULT_SENSITIVITY,
    val triggerDurationSeconds: Int = SettingsDataStore.DEFAULT_TRIGGER_DURATION_S,
    val cooldownDurationSeconds: Int = SettingsDataStore.DEFAULT_COOLDOWN_DURATION_S,
    val watchVibrationEnabled: Boolean = SettingsDataStore.DEFAULT_WATCH_VIBRATION_ENABLED,
    val phoneVibrationEnabled: Boolean = SettingsDataStore.DEFAULT_PHONE_VIBRATION_ENABLED,
    val phoneSoundEnabled: Boolean = SettingsDataStore.DEFAULT_PHONE_SOUND_ENABLED,
    val vibrationStrong: Boolean = SettingsDataStore.DEFAULT_VIBRATION_STRONG,
    val debugMode: Boolean = SettingsDataStore.DEFAULT_DEBUG_MODE
)
