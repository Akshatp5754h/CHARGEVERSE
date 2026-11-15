package com.akshat.chargeverse.ui.animations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.data.db.AnimationCategory
import com.akshat.chargeverse.data.db.AnimationProfileEntity
import com.akshat.chargeverse.data.db.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** S3: library sort orders offered by the sort menu. */
enum class AnimationsSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME("Name"),
    LARGEST("Largest"),
}

/**
 * Filter/search/sort selection bundled into one flow so the main [combine]
 * stays within its five-flow overload.
 */
private data class LibraryFilters(
    val category: AnimationCategory? = null,
    val query: String = "",
    val sort: AnimationsSort = AnimationsSort.NEWEST,
    /** Media id currently showing the "profile created" hint in the overlay. */
    val profileCreatedFor: Long? = null,
)

data class AnimationsUiState(
    /** Filtered + sorted items shown in the grid. */
    val items: List<MediaItemEntity> = emptyList(),
    /** Unfiltered library size, for the "N in library" header. */
    val totalCount: Int = 0,
    /** Per-category counts for the filter chips. */
    val counts: Map<AnimationCategory, Int> = emptyMap(),
    val category: AnimationCategory? = null,
    val query: String = "",
    val sort: AnimationsSort = AnimationsSort.NEWEST,
    val importing: Boolean = false,
    val error: String? = null,
    /** SAF uri awaiting a category decision (import dialog). */
    val pendingUri: android.net.Uri? = null,
    /** Media id for which a profile was just created (S3 overlay hint). */
    val profileCreatedFor: Long? = null,
)

class AnimationsViewModel : ViewModel() {

    private val db = ServiceLocator.db
    private val settings = ServiceLocator.settings
    private val resolver = ServiceLocator.mediaResolver

    private val filters = MutableStateFlow(LibraryFilters())
    private val importing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val pendingUri = MutableStateFlow<android.net.Uri?>(null)

    val ui: StateFlow<AnimationsUiState> = combine(
        db.mediaItemDao().observeAll(),
        filters,
        importing,
        error,
        pendingUri,
    ) { all, f, importing, error, pending ->
        AnimationsUiState(
            items = all
                .filter { f.category == null || it.category == f.category }
                .filter { matchesQuery(it, f.query) }
                .sortedWith(sortComparator(f.sort)),
            totalCount = all.size,
            counts = AnimationCategory.entries.associateWith { cat ->
                all.count { it.category == cat }
            },
            category = f.category,
            query = f.query,
            sort = f.sort,
            importing = importing,
            error = error,
            pendingUri = pending,
            profileCreatedFor = f.profileCreatedFor,
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
        filters.value = filters.value.copy(category = category)
    }

    /** S3: client-side search over title + category name. */
    fun setQuery(query: String) {
        filters.value = filters.value.copy(query = query)
    }

    fun setSort(sort: AnimationsSort) {
        filters.value = filters.value.copy(sort = sort)
    }

    fun stageImport(uri: android.net.Uri) {
        // One import at a time; the FAB is hidden while importing anyway.
        if (importing.value) return
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

    /**
     * S3 "Use in new profile": the same creation flow Studio uses (see
     * StudioViewModel.createProfile) — a bare profile pointing at this media.
     * The profile EDITOR is Studio-owned, so the overlay pairs the button
     * with an "Edit profiles in Studio" hint.
     */
    fun createProfile(media: MediaItemEntity) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        db.animationProfileDao().insert(
            AnimationProfileEntity(
                name = media.title.ifEmpty { "Profile" },
                mediaItemId = media.id,
                createdAtMs = now,
                updatedAtMs = now,
            )
        )
        filters.value = filters.value.copy(profileCreatedFor = media.id)
    }

    /** Clears the "profile created" hint (called when the overlay changes). */
    fun clearProfileHint() {
        filters.value = filters.value.copy(profileCreatedFor = null)
    }

    fun dismissError() {
        error.value = null
    }

    private fun matchesQuery(item: MediaItemEntity, raw: String): Boolean {
        val q = raw.trim()
        if (q.isEmpty()) return true
        return item.title.contains(q, ignoreCase = true) ||
            item.category.name.lowercase().replace('_', ' ').contains(q, ignoreCase = true)
    }

    private fun sortComparator(sort: AnimationsSort): Comparator<MediaItemEntity> = when (sort) {
        AnimationsSort.NEWEST -> compareByDescending { it: MediaItemEntity -> it.addedAtMs }
        AnimationsSort.OLDEST -> compareBy { it: MediaItemEntity -> it.addedAtMs }
        AnimationsSort.NAME -> compareBy { it: MediaItemEntity -> it.title.lowercase() }
        AnimationsSort.LARGEST -> compareByDescending { it: MediaItemEntity -> it.sizeBytes }
    }
}
