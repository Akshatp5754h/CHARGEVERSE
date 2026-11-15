package com.akshat.chargeverse.ui.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.battery.BatteryReading
import com.akshat.chargeverse.battery.BatteryRepository
import com.akshat.chargeverse.battery.ChargingEvent
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.data.db.AnimationProfileEntity
import com.akshat.chargeverse.data.db.MediaItemEntity
import com.akshat.chargeverse.data.prefs.CvSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ChargingViewModel for the full-screen experience (v2/S4).
 *
 * The active profile and settings are observed CONTINUOUSLY, so an automation
 * APPLY_PROFILE (or a Settings change) mid-session updates the playing media
 * live, without restarting the activity. Selection rules:
 *  - a profile explicitly made active mid-session (chips or automation) always
 *    wins, regardless of the random setting;
 *  - otherwise, random-enabled sessions play a sticky random pick;
 *  - otherwise the active profile plays, falling back to a random one when it
 *    is deleted, and finally to null → the ambient fallback screen (never a
 *    crash).
 */
class ChargingViewModel : ViewModel() {

    private val battery: BatteryRepository = ServiceLocator.battery
    private val db = ServiceLocator.db
    private val settingsRepo = ServiceLocator.settings

    val state: StateFlow<BatteryReading?> = battery.reading
        .stateIn(viewModelScope, SharingStarted.Eagerly, battery.reading.value)

    val events = battery.events

    val sessionSettings: StateFlow<CvSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, CvSettings())

    /** Every profile, for the control bar's chips row. */
    val profiles: StateFlow<List<AnimationProfileEntity>> = db.animationProfileDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Profile chosen for this charging session (live). */
    private val _profile = MutableStateFlow<AnimationProfileEntity?>(null)
    val profile: StateFlow<AnimationProfileEntity?> = _profile.asStateFlow()

    /** Media backing the profile. */
    private val _media = MutableStateFlow<MediaItemEntity?>(null)
    val media: StateFlow<MediaItemEntity?> = _media.asStateFlow()

    /** Active profile id when the session started, to detect mid-session picks. */
    private var sessionStartActiveId: Long? = null

    /** Sticky random pick for the session; survives unrelated setting churn. */
    private var sessionPickId: Long? = null

    init {
        viewModelScope.launch {
            sessionStartActiveId = db.animationProfileDao().active()?.id
            combine(
                db.animationProfileDao().observeActive(),
                settingsRepo.settings,
                db.animationProfileDao().observeAll(),
            ) { active, settings, all -> Triple(active, settings, all) }
                .collect { (active, settings, all) ->
                    val chosen = chooseProfile(active, settings, all)
                    val previous = _profile.value
                    _profile.value = chosen
                    if (chosen?.mediaItemId != previous?.mediaItemId) {
                        _media.value = chosen?.let { db.mediaItemDao().byId(it.mediaItemId) }
                    }
                }
        }
    }

    /**
     * Session selection rule (see class Kdoc). The sticky pick is only
     * re-rolled when it no longer exists, so unrelated DataStore emissions
     * never reshuffle the playing media.
     */
    private fun chooseProfile(
        active: AnimationProfileEntity?,
        settings: CvSettings,
        all: List<AnimationProfileEntity>,
    ): AnimationProfileEntity? {
        val midSessionSelection = active != null && active.id != sessionStartActiveId
        val candidate = when {
            midSessionSelection -> active
            settings.randomAnimationEnabled -> null
            else -> active
        }
        if (candidate != null) {
            sessionPickId = null
            return candidate
        }
        val held = sessionPickId?.let { id -> all.firstOrNull { it.id == id } }
        return held ?: all.randomOrNull()?.also { sessionPickId = it.id }
    }

    /** Control bar: switch media live and make the chip's profile active. */
    fun selectProfile(profile: AnimationProfileEntity) = viewModelScope.launch {
        db.animationProfileDao().clearActive()
        db.animationProfileDao().setActive(profile.id)
        settingsRepo.setActiveProfile(profile.id)
        // An explicit chip selection ends the session's random pick.
        settingsRepo.setRandomAnimation(false)
    }

    /** Control bar Fit/Fill pill: persists on the profile (schema v3). */
    fun setFillMode(fill: Boolean) = viewModelScope.launch {
        val current = _profile.value ?: return@launch
        db.animationProfileDao().update(
            current.copy(fillMode = fill, updatedAtMs = System.currentTimeMillis())
        )
    }
}
