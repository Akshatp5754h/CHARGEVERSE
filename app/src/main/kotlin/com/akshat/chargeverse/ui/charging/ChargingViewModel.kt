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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ChargingViewModel for the full-screen experience. */
class ChargingViewModel : ViewModel() {

    private val battery: BatteryRepository = ServiceLocator.battery
    private val db = ServiceLocator.db
    private val settingsRepo = ServiceLocator.settings

    val state: StateFlow<BatteryReading?> = battery.reading
        .stateIn(viewModelScope, SharingStarted.Eagerly, battery.reading.value)

    val events = battery.events

    /** Active (or random) profile chosen for this charging session. */
    private val _profile = MutableStateFlow<AnimationProfileEntity?>(null)
    val profile: StateFlow<AnimationProfileEntity?> = _profile.asStateFlow()

    /** Media backing the profile. */
    private val _media = MutableStateFlow<MediaItemEntity?>(null)
    val media: StateFlow<MediaItemEntity?> = _media.asStateFlow()

    private val _sessionSettings = MutableStateFlow(CvSettings())
    val sessionSettings: StateFlow<CvSettings> = _sessionSettings.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settingsRepo.current()
            _sessionSettings.value = s
            val active = db.animationProfileDao().active()
            val chosen = if (s.randomAnimationEnabled) {
                db.animationProfileDao().all().randomOrNull()
            } else {
                active
            }
            _profile.value = chosen
            _media.value = chosen?.let { db.mediaItemDao().byId(it.mediaItemId) }
        }
    }
}
