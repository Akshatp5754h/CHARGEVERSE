package com.akshat.chargeverse.ui.animations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.core.media.MediaResolver
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
)

class AnimationsViewModel : ViewModel() {

    private val db = ServiceLocator.db
    private val resolver = MediaResolver(ServiceLocator.app)

    private val selectedCategory = MutableStateFlow<AnimationCategory?>(null)
    private val importing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val ui: StateFlow<AnimationsUiState> = combine(
        db.mediaItemDao().observeAll(),
        selectedCategory,
        importing,
        error,
    ) { items, category, importing, error ->
        AnimationsUiState(
            items = if (category == null) items else items.filter { it.category == category },
            category = category,
            importing = importing,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AnimationsUiState())

    fun selectCategory(category: AnimationCategory?) {
        selectedCategory.value = category
    }

    /** Imports a picked SAF uri into private storage and registers it in Room. */
    fun import(uri: android.net.Uri) = viewModelScope.launch {
        importing.value = true
        error.value = null
        withContext(Dispatchers.IO) {
            runCatching {
                val resolved = resolver.resolveAndCopy(uri, AnimationCategory.ABSTRACT)
                db.mediaItemDao().insert(
                    MediaItemEntity(
                        type = resolved.type,
                        uri = resolved.uri,
                        title = resolved.title,
                        category = AnimationCategory.ABSTRACT,
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

    fun delete(item: MediaItemEntity) = viewModelScope.launch {
        withContext(Dispatchers.IO) { resolver.deleteCopy(item.uri) }
        db.mediaItemDao().delete(item.id)
    }

    fun dismissError() {
        error.value = null
    }
}
