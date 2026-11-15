package com.akshat.chargeverse.ui.sounds

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.core.media.MediaResolver
import com.akshat.chargeverse.data.db.SoundItemEntity
import com.akshat.chargeverse.data.db.SoundRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SoundsUiState(
    val sounds: List<SoundItemEntity> = emptyList(),
    val volume: Float = 0.8f,
    val muted: Boolean = false,
    val repeat: Boolean = false,
    val connectSoundId: Long = -1,
    val disconnectSoundId: Long = -1,
    val fullSoundId: Long = -1,
    val lowSoundId: Long = -1,
    val previewingId: Long? = null,
    val error: String? = null,
)

class SoundsViewModel : ViewModel() {

    private val db = ServiceLocator.db
    private val settings = ServiceLocator.settings
    private val soundEngine = ServiceLocator.soundEngine
    private val resolver = MediaResolver(ServiceLocator.app)

    private val previewingId = MutableStateFlow<Long?>(null)
    private val error = MutableStateFlow<String?>(null)

    val ui: StateFlow<SoundsUiState> = combine(
        db.soundItemDao().observeAll(),
        settings.settings,
        previewingId,
        error,
    ) { sounds, s, previewing, err ->
        SoundsUiState(
            sounds = sounds,
            volume = s.soundVolume,
            muted = s.masterMuted,
            repeat = s.repeatChargingSound,
            connectSoundId = s.connectSoundId,
            disconnectSoundId = s.disconnectSoundId,
            fullSoundId = s.fullSoundId,
            lowSoundId = s.lowSoundId,
            previewingId = previewing,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SoundsUiState())

    /** Imports a picked audio file into private storage (no storage permission needed). */
    fun importAudio(uri: Uri, role: SoundRole) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = resolver.resolveAudio(uri, role.name)
                db.soundItemDao().insert(
                    SoundItemEntity(
                        name = file.name.removePrefix("snd_"),
                        uri = Uri.fromFile(file).toString(),
                        role = role,
                        isBuiltIn = false,
                        addedAtMs = System.currentTimeMillis(),
                    )
                )
            }.onFailure { error.value = "Audio import failed: ${it.message}" }
        }
    }

    fun preview(item: SoundItemEntity) = viewModelScope.launch {
        previewingId.value = item.id
        soundEngine.preview(Uri.parse(item.uri))
        previewingId.value = null
    }

    fun assignRole(item: SoundItemEntity, role: SoundRole) = viewModelScope.launch {
        val newId = db.soundItemDao().insert(item.copy(role = role, id = 0))
        // also register as the role's active sound
        when (role) {
            SoundRole.CONNECT -> settings.setConnectSound(newId)
            SoundRole.DISCONNECT -> settings.setDisconnectSound(newId)
            SoundRole.FULL -> settings.setFullSound(newId)
            SoundRole.LOW -> settings.setLowSound(newId)
        }
    }

    fun delete(item: SoundItemEntity) = viewModelScope.launch {
        withContext(Dispatchers.IO) { runCatching { java.io.File(Uri.parse(item.uri).path!!).delete() } }
        db.soundItemDao().delete(item.id)
    }

    fun setVolume(v: Float) = viewModelScope.launch {
        settings.setSoundVolume(v)
        soundEngine.volume = v
    }

    fun setMuted(muted: Boolean) = viewModelScope.launch {
        settings.setMasterMuted(muted)
        soundEngine.muted = muted
        if (muted) soundEngine.stop()
    }

    fun setRepeat(repeat: Boolean) = viewModelScope.launch { settings.setRepeatSound(repeat) }

    fun stopPreview() = soundEngine.stop()

    fun dismissError() {
        error.value = null
    }
}
