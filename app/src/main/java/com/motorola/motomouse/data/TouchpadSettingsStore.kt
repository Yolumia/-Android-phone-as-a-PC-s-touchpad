package com.motorola.motomouse.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.touchpadSettingsDataStore by preferencesDataStore(name = "motomouse_touchpad_settings")

data class TouchpadHapticSettings(
    val enabled: Boolean = true,
    val intensity: Float = 0.62f,
    val frequency: Float = 0.58f,
)

class TouchpadSettingsStore(private val context: Context) {
    val settings: Flow<TouchpadHapticSettings> = context.touchpadSettingsDataStore.data.map { preferences ->
        TouchpadHapticSettings(
            enabled = preferences[KEY_HAPTICS_ENABLED] ?: true,
            intensity = (preferences[KEY_HAPTIC_INTENSITY] ?: 0.62f).coerceIn(0f, 1f),
            frequency = (preferences[KEY_HAPTIC_FREQUENCY] ?: 0.58f).coerceIn(0f, 1f),
        )
    }

    suspend fun updateEnabled(enabled: Boolean) {
        context.touchpadSettingsDataStore.edit { preferences ->
            preferences[KEY_HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun updateIntensity(intensity: Float) {
        context.touchpadSettingsDataStore.edit { preferences ->
            preferences[KEY_HAPTIC_INTENSITY] = intensity.coerceIn(0f, 1f)
        }
    }

    suspend fun updateFrequency(frequency: Float) {
        context.touchpadSettingsDataStore.edit { preferences ->
            preferences[KEY_HAPTIC_FREQUENCY] = frequency.coerceIn(0f, 1f)
        }
    }

    private companion object {
        val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val KEY_HAPTIC_INTENSITY = floatPreferencesKey("haptic_intensity")
        val KEY_HAPTIC_FREQUENCY = floatPreferencesKey("haptic_frequency")
    }
}

