package com.akshat.chargeverse.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chargeverse_settings")

/** All persisted CHARGEVERSE settings. */
data class CvSettings(
    val activeProfileId: Long = -1L,
    val randomAnimationEnabled: Boolean = false,
    val loopAnimation: Boolean = true,
    val showOnChargerConnect: Boolean = true,
    val keepScreenOnWhileCharging: Boolean = true,
    val overlayModeEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val masterMuted: Boolean = false,
    val soundVolume: Float = 0.8f,
    val repeatChargingSound: Boolean = false,
    val connectSoundId: Long = -1L,
    val disconnectSoundId: Long = -1L,
    val fullSoundId: Long = -1L,
    val lowSoundId: Long = -1L,
    val fullBatteryAlertEnabled: Boolean = true,
    val lowBatteryAlertEnabled: Boolean = true,
    val lowBatteryThreshold: Int = 15,
    val aiEnabled: Boolean = false,
    val aiServerUrl: String = "http://127.0.0.1:8188",
    val onboardingDone: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ACTIVE_PROFILE = longPreferencesKey("active_profile_id")
        val RANDOM_ANIMATION = booleanPreferencesKey("random_animation")
        val LOOP_ANIMATION = booleanPreferencesKey("loop_animation")
        val SHOW_ON_CONNECT = booleanPreferencesKey("show_on_connect")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val OVERLAY_MODE = booleanPreferencesKey("overlay_mode")
        val HAPTICS = booleanPreferencesKey("haptics")
        val MASTER_MUTED = booleanPreferencesKey("master_muted")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val REPEAT_SOUND = booleanPreferencesKey("repeat_sound")
        val CONNECT_SOUND = longPreferencesKey("connect_sound_id")
        val DISCONNECT_SOUND = longPreferencesKey("disconnect_sound_id")
        val FULL_SOUND = longPreferencesKey("full_sound_id")
        val LOW_SOUND = longPreferencesKey("low_sound_id")
        val FULL_ALERT = booleanPreferencesKey("full_alert_enabled")
        val LOW_ALERT = booleanPreferencesKey("low_alert_enabled")
        val LOW_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_URL = stringPreferencesKey("ai_server_url")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<CvSettings> = context.dataStore.data.map { p ->
        CvSettings(
            activeProfileId = p[Keys.ACTIVE_PROFILE] ?: -1L,
            randomAnimationEnabled = p[Keys.RANDOM_ANIMATION] ?: false,
            loopAnimation = p[Keys.LOOP_ANIMATION] ?: true,
            showOnChargerConnect = p[Keys.SHOW_ON_CONNECT] ?: true,
            keepScreenOnWhileCharging = p[Keys.KEEP_SCREEN_ON] ?: true,
            overlayModeEnabled = p[Keys.OVERLAY_MODE] ?: false,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            masterMuted = p[Keys.MASTER_MUTED] ?: false,
            soundVolume = p[Keys.SOUND_VOLUME] ?: 0.8f,
            repeatChargingSound = p[Keys.REPEAT_SOUND] ?: false,
            connectSoundId = p[Keys.CONNECT_SOUND] ?: -1L,
            disconnectSoundId = p[Keys.DISCONNECT_SOUND] ?: -1L,
            fullSoundId = p[Keys.FULL_SOUND] ?: -1L,
            lowSoundId = p[Keys.LOW_SOUND] ?: -1L,
            fullBatteryAlertEnabled = p[Keys.FULL_ALERT] ?: true,
            lowBatteryAlertEnabled = p[Keys.LOW_ALERT] ?: true,
            lowBatteryThreshold = p[Keys.LOW_THRESHOLD] ?: 15,
            aiEnabled = p[Keys.AI_ENABLED] ?: false,
            aiServerUrl = p[Keys.AI_URL] ?: "http://127.0.0.1:8188",
            onboardingDone = p[Keys.ONBOARDING] ?: false,
        )
    }

    suspend fun setActiveProfile(id: Long) = context.dataStore.edit { it[Keys.ACTIVE_PROFILE] = id }
    suspend fun setRandomAnimation(v: Boolean) = context.dataStore.edit { it[Keys.RANDOM_ANIMATION] = v }
    suspend fun setLoopAnimation(v: Boolean) = context.dataStore.edit { it[Keys.LOOP_ANIMATION] = v }
    suspend fun setShowOnConnect(v: Boolean) = context.dataStore.edit { it[Keys.SHOW_ON_CONNECT] = v }
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = v }
    suspend fun setOverlayMode(v: Boolean) = context.dataStore.edit { it[Keys.OVERLAY_MODE] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[Keys.HAPTICS] = v }
    suspend fun setMasterMuted(v: Boolean) = context.dataStore.edit { it[Keys.MASTER_MUTED] = v }
    suspend fun setSoundVolume(v: Float) = context.dataStore.edit { it[Keys.SOUND_VOLUME] = v }
    suspend fun setRepeatSound(v: Boolean) = context.dataStore.edit { it[Keys.REPEAT_SOUND] = v }
    suspend fun setConnectSound(id: Long) = context.dataStore.edit { it[Keys.CONNECT_SOUND] = id }
    suspend fun setDisconnectSound(id: Long) = context.dataStore.edit { it[Keys.DISCONNECT_SOUND] = id }
    suspend fun setFullSound(id: Long) = context.dataStore.edit { it[Keys.FULL_SOUND] = id }
    suspend fun setLowSound(id: Long) = context.dataStore.edit { it[Keys.LOW_SOUND] = id }
    suspend fun setFullAlert(v: Boolean) = context.dataStore.edit { it[Keys.FULL_ALERT] = v }
    suspend fun setLowAlert(v: Boolean) = context.dataStore.edit { it[Keys.LOW_ALERT] = v }
    suspend fun setLowThreshold(v: Int) = context.dataStore.edit { it[Keys.LOW_THRESHOLD] = v }
    suspend fun setAiEnabled(v: Boolean) = context.dataStore.edit { it[Keys.AI_ENABLED] = v }
    suspend fun setAiUrl(v: String) = context.dataStore.edit { it[Keys.AI_URL] = v }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[Keys.ONBOARDING] = v }

    /** One-shot read for receivers/services that need the current settings. */
    suspend fun current(): CvSettings = settings.first()
}
