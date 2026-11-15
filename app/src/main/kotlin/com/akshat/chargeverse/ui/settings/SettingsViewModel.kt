package com.akshat.chargeverse.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.core.media.MediaResolver
import com.akshat.chargeverse.data.prefs.ChargingOverlay
import com.akshat.chargeverse.data.prefs.CvSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: CvSettings = CvSettings(),
    val storageUsedMb: Float = 0f,
    val aiServerReachable: Boolean? = null,
)

class SettingsViewModel : ViewModel() {

    private val settings = ServiceLocator.settings
    private val comfy = ServiceLocator.comfyClient
    private val resolver = MediaResolver(ServiceLocator.app)

    private val storage = MutableStateFlow(0f)
    private val aiReachable = MutableStateFlow<Boolean?>(null)

    val ui: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
        settings.settings,
        storage,
        aiReachable,
    ) { s, usedMb, reachable ->
        SettingsUiState(settings = s, storageUsedMb = usedMb, aiServerReachable = reachable)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    init {
        viewModelScope.launch { storage.value = resolver.totalBytesUsed() / (1024f * 1024f) }
    }

    fun setShowOnConnect(v: Boolean) = viewModelScope.launch { settings.setShowOnConnect(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { settings.setKeepScreenOn(v) }

    /** v2/S4: one overlay toggle in the `overlay_mode` CSV. */
    fun setOverlayEnabled(overlay: ChargingOverlay, enabled: Boolean) =
        viewModelScope.launch { settings.setOverlayEnabled(overlay, enabled) }

    fun setHaptics(v: Boolean) = viewModelScope.launch { settings.setHaptics(v) }
    fun setLoop(v: Boolean) = viewModelScope.launch { settings.setLoopAnimation(v) }
    fun setRandom(v: Boolean) = viewModelScope.launch { settings.setRandomAnimation(v) }
    fun setFullAlert(v: Boolean) = viewModelScope.launch { settings.setFullAlert(v) }
    fun setLowAlert(v: Boolean) = viewModelScope.launch { settings.setLowAlert(v) }
    fun setLowThreshold(v: Int) = viewModelScope.launch { settings.setLowThreshold(v) }
    fun setAiEnabled(v: Boolean) = viewModelScope.launch { settings.setAiEnabled(v) }
    fun setAiUrl(v: String) = viewModelScope.launch { settings.setAiUrl(v) }

    /** Tests connectivity to the user's local ComfyUI server. */
    fun pingAi() = viewModelScope.launch {
        aiReachable.value = null
        val s = settings.current()
        val result = comfy.ping(s.aiServerUrl)
        aiReachable.value = result.ok
    }
}
