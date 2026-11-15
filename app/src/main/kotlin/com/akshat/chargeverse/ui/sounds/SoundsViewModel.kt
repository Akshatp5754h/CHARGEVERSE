package com.akshat.chargeverse.ui.sounds

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.core.di.ServiceLocator
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
    /** All role rows: soundItemId → assigned roles (a sound may serve several). */
    val rolesBySound: Map<Long, List<SoundRole>> = emptyMap(),
    val volume: Float = 0.8f,
    val muted: Boolean = false,
    val repeat: Boolean = false,
    val connectSoundId: Long = -1,
    val disconnectSoundId: Long = -1,
    val fullSoundId: Long = -1,
    val lowSoundId: Long = -1,
    val previewingId: Long? = null,
    /** SAF uri awaiting a role decision (import dialog). */
    val pendingImportUri: Uri? = null,
    val error: String? = null,
)

class SoundsViewModel : ViewModel() {

    private val db = ServiceLocator.db
    private val settings = ServiceLocator.settings
    private val soundEngine = ServiceLocator.soundEngine
    private val resolver = ServiceLocator.mediaResolver

    private val previewingId = MutableStateFlow<Long?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val pendingImportUri = MutableStateFlow<Uri?>(null)

    val ui: StateFlow<SoundsUiState> = combine(
        combine(
            db.soundItemDao().observeAll(),
            db.soundItemDao().observeAllRoles(),
            settings.settings,
            previewingId,
            pendingImportUri,
        ) { sounds, roleRows, s, previewing, pending ->
            // Base screen state without the transient error line.
            SoundsUiState(
                sounds = sounds,
                rolesBySound = roleRows.groupBy({ it.soundItemId }, { it.role }),
                volume = s.soundVolume,
                muted = s.masterMuted,
                repeat = s.repeatChargingSound,
                connectSoundId = s.connectSoundId,
                disconnectSoundId = s.disconnectSoundId,
                fullSoundId = s.fullSoundId,
                lowSoundId = s.lowSoundId,
                previewingId = previewing,
                pendingImportUri = pending,
            )
        },
        error,
    ) { base, err ->
        base.copy(error = err)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SoundsUiState())

    /** Stages a picked audio file; the UI then asks for its role(s). */
    fun stageImport(uri: Uri) {
        pendingImportUri.value = uri
    }

    fun cancelImport() {
        pendingImportUri.value = null
    }

    /**
     * Imports a picked audio file into private storage (no storage permission
     * needed) under the chosen role(s). The stored copy keeps a uuid + the
     * source extension; the display name is the original file name.
     */
    fun importAudio(uri: Uri, roles: Set<SoundRole>) = viewModelScope.launch {
        pendingImportUri.value = null
        withContext(Dispatchers.IO) {
            runCatching {
                val audio = resolver.resolveAudio(uri)
                val id = db.soundItemDao().insert(
                    SoundItemEntity(
                        name = audio.displayName,
                        uri = Uri.fromFile(audio.file).toString(),
                        isBuiltIn = false,
                        addedAtMs = System.currentTimeMillis(),
                    )
                )
                roles.forEach { db.soundItemDao().assignRole(id, it) }
                // The imported sound serves the roles the user just picked.
                roles.forEach { role -> setActiveSound(role, id) }
            }.onFailure { error.value = "Audio import failed: ${it.message}" }
        }
    }

    /** Reassigns a sound's roles from the per-row role chip (multi-select). */
    fun setRoles(item: SoundItemEntity, roles: Set<SoundRole>) = viewModelScope.launch {
        val previous = db.soundItemDao().rolesFor(item.id).toSet()
        db.soundItemDao().clearRoles(item.id)
        roles.forEach { db.soundItemDao().assignRole(item.id, it) }

        // Newly assigned roles start serving events right away (the old
        // assignRole semantics); removed roles fall back to the chime.
        SoundRole.entries.forEach { role ->
            val activeId = activeSoundFor(settings.current(), role)
            when {
                role in roles && role !in previous && activeId != item.id -> setActiveSound(role, item.id)
                role !in roles && activeId == item.id -> setActiveSound(role, -1L)
            }
        }
    }

    fun preview(item: SoundItemEntity) = viewModelScope.launch {
        previewingId.value = item.id
        soundEngine.preview(Uri.parse(item.uri))
        previewingId.value = null
    }

    /**
     * Deletes a sound: removes its private file, cleans its role rows (FK
     * cascade also covers this) and re-points any settings that referenced it
     * to another sound of the same role — preferring the built-in chime.
     */
    fun delete(item: SoundItemEntity) = viewModelScope.launch {
        if (item.isBuiltIn) {
            error.value = "Built-in sounds can't be deleted"
            return@launch
        }
        val roles = db.soundItemDao().rolesFor(item.id)
        withContext(Dispatchers.IO) {
            runCatching { java.io.File(Uri.parse(item.uri).path!!).delete() }
        }
        db.soundItemDao().delete(item.id)

        val s = settings.current()
        for (role in roles) {
            if (activeSoundFor(s, role) == item.id) {
                val replacement = db.soundItemDao().byRole(role)
                    .firstOrNull { it.isBuiltIn }
                    ?: db.soundItemDao().byRole(role).firstOrNull()
                setActiveSound(role, replacement?.id ?: -1L)
            }
        }
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

    private fun activeSoundFor(s: com.akshat.chargeverse.data.prefs.CvSettings, role: SoundRole): Long =
        when (role) {
            SoundRole.CONNECT -> s.connectSoundId
            SoundRole.DISCONNECT -> s.disconnectSoundId
            SoundRole.FULL -> s.fullSoundId
            SoundRole.LOW -> s.lowSoundId
        }

    private suspend fun setActiveSound(role: SoundRole, id: Long) {
        when (role) {
            SoundRole.CONNECT -> settings.setConnectSound(id)
            SoundRole.DISCONNECT -> settings.setDisconnectSound(id)
            SoundRole.FULL -> settings.setFullSound(id)
            SoundRole.LOW -> settings.setLowSound(id)
        }
    }
}
