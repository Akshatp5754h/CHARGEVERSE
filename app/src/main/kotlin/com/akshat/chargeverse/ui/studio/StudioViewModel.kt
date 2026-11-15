package com.akshat.chargeverse.ui.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.data.db.AnimationProfileEntity
import com.akshat.chargeverse.data.db.MediaItemEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudioUiState(
    val profiles: List<AnimationProfileEntity> = emptyList(),
    val media: List<MediaItemEntity> = emptyList(),
    val editing: AnimationProfileEntity? = null,
)

class StudioViewModel : ViewModel() {

    private val db = ServiceLocator.db
    private val settings = ServiceLocator.settings

    private val editing = MutableStateFlow<AnimationProfileEntity?>(null)

    val ui: StateFlow<StudioUiState> = combine(
        db.animationProfileDao().observeAll(),
        db.mediaItemDao().observeAll(),
        editing,
    ) { profiles, media, editing ->
        StudioUiState(profiles, media, editing)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StudioUiState())

    /** Creates a profile from a media item and opens it in the editor. */
    fun createProfile(media: MediaItemEntity) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val id = db.animationProfileDao().insert(
            AnimationProfileEntity(
                name = media.title.ifEmpty { "Profile" },
                mediaItemId = media.id,
                createdAtMs = now,
                updatedAtMs = now,
            )
        )
        editing.value = db.animationProfileDao().byId(id)
    }

    fun openEditor(profile: AnimationProfileEntity?) {
        editing.value = profile
    }

    fun saveEdits(profile: AnimationProfileEntity) = viewModelScope.launch {
        db.animationProfileDao().update(profile.copy(updatedAtMs = System.currentTimeMillis()))
        editing.value = null
    }

    fun activate(profile: AnimationProfileEntity) = viewModelScope.launch {
        db.animationProfileDao().clearActive()
        db.animationProfileDao().setActive(profile.id)
        settings.setActiveProfile(profile.id)
    }

    fun delete(profile: AnimationProfileEntity) = viewModelScope.launch {
        db.animationProfileDao().delete(profile.id)
    }
}
