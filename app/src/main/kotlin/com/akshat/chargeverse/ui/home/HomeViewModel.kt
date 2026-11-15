package com.akshat.chargeverse.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.battery.BatteryReading
import com.akshat.chargeverse.battery.ChargeSource
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.data.db.AnimationProfileEntity
import com.akshat.chargeverse.data.db.MediaItemEntity
import com.akshat.chargeverse.data.prefs.CvSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val battery: BatteryReading? = null,
    val settings: CvSettings = CvSettings(),
    val activeProfile: AnimationProfileEntity? = null,
    val mediaCount: Int = 0,
) {
    val chargingSourceLabel: String
        get() = when (battery?.source) {
            ChargeSource.AC -> "AC adapter"
            ChargeSource.USB -> "USB"
            ChargeSource.WIRELESS -> "Wireless"
            ChargeSource.DOCK -> "Dock"
            ChargeSource.OTHER -> "Connected"
            else -> "Not charging"
        }
}

class HomeViewModel : ViewModel() {

    private val battery = ServiceLocator.battery
    private val settings = ServiceLocator.settings
    private val db = ServiceLocator.db

    val ui: StateFlow<HomeUiState> = combine(
        battery.reading,
        settings.settings,
        db.animationProfileDao().observeActive(),
    ) { reading, s, profile ->
        HomeUiState(
            battery = reading,
            settings = s,
            activeProfile = profile,
            mediaCount = db.mediaItemDao().count(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    fun setActiveProfile(id: Long) = viewModelScope.launch {
        db.animationProfileDao().clearActive()
        db.animationProfileDao().setActive(id)
        settings.setActiveProfile(id)
    }
}
