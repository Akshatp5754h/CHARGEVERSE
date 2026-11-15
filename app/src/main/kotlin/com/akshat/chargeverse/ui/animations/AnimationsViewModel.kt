package com.akshat.chargeverse.ui.animations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.data.db.AnimationCategory
import com.akshat.chargeverse.data.db.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnimationsUiState(
    val items: List<MediaItemEntity> = emptyList(),
    val category: AnimationCategory? = null,
    val importing: Boolean = false,
    val error: String? = null,
    /** SAF uri awaiting a category decision (import dialog). */
    val pendingUri: android.net.Uri? = null,
)

class AnimationsViewModel : ViewModel() {

    private val db = ServiceLocator.db
    private val settings = ServiceLocator.settings
    private val resolver = ServiceLocator.mediaResolver

    private val selectedCategory = MutableStateFlow<AnimationCategory?>(null)
    private val importing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val pendingUri = MutableStateFlow<android.net.Uri?>(null)

    val ui: StateFlow<AnimationsUiState> = combine(
        db.mediaItemDao().observeAll(),
        selectedCategory,
        importing,
        error,
        pendingUri,
    ) { items, category, importing, error, pending ->
        AnimationsUiState(
            items = if (category == null) items else items.filter { it.category == category },
            category = category,
            importing = importing,
            error = error,
            pendingUri = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AnimationsUiState())

    init {
        // D14: if the active profile vanishes (e.g. FK cascade after its media
        // was deleted), clear the stale DataStore pointer.
        viewModelScope.launch {
            db.animationProfileDao().observeAll().collect { profiles ->
                val activeId = settings.current().activeProfileId
                if (activeId > 0 && profiles.none { it.id == activeId }) {
                    settings.setActiveProfile(-1L)
                }
            }
        }
    }

    fun selectCategory(category: AnimationCategory?) {
        selectedCategory.value = category
    }

    fun stageImport(uri: android.net.Uri) {
        pendingUri.value = uri
    }

    fun cancelImport() {
        pendingUri.value = null
    }

    /** Imports the staged uri into private storage under the chosen category. */
    fun import(uri: android.net.Uri, category: AnimationCategory) = viewModelScope.launch {
        pendingUri.value = null
        importing.value = true
        error.value = null
        withContext(Dispatchers.IO) {
            runCatching {
                val resolved = resolver.resolveAndCopy(uri, category)
                db.mediaItemDao().insert(
                    MediaItemEntity(
                        type = resolved.type,
                        uri = resolved.uri,
                        title = resolved.title,
                        category = category,
                        addedAtMs = System.currentTimeMillis(),
                        sizeBytes = resolved.sizeBytes,
                        isBuiltIn = false,
                        durationMs = resolved.durationMs,
                        widthPx = resolved.widthPx,
                        heightPx = resolved.heightPx,
                    )
                )
            }.onFailure { error.value = "Import failed: ${it.message}" }
        }
        importing.value = false
    }

    /**
     * Deletes a media item: removes its private copy; the DB FK cascade then
     * removes any profiles referencing it (D14), and if the ACTIVE profile
     * was among them the active pointers are cleared so the charging
     * experience never points at a missing profile.
     */
    fun delete(item: MediaItemEntity) = viewModelScope.launch {
        val referencingProfiles = db.animationProfileDao().all().filter { it.mediaItemId == item.id }
        val activeRemoved = referencingProfiles.any { it.isActive }
        withContext(Dispatchers.IO) { resolver.deleteCopy(item.uri) }
        db.mediaItemDao().delete(item.id)
        if (activeRemoved) {
            db.animationProfileDao().clearActive()
            settings.setActiveProfile(-1L)
        }
    }

    fun dismissError() {
        error.value = null
    }
}
