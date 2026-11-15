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

data class HomeUiState(
    val battery: BatteryReading? = null,
    val settings: CvSettings = CvSettings(),
    val activeProfile: AnimationProfileEntity? = null,
    /** The active profile's media, resolved from the live library. */
    val activeProfileMedia: MediaItemEntity? = null,
    /** Reactive library (newest first) — drives count, recents and Discover. */
    val library: List<MediaItemEntity> = emptyList(),
    val recent: List<MediaItemEntity> = emptyList(),
    val discover: List<MediaItemEntity> = emptyList(),
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

    /** Hero state label per §5: Charging on AC/USB/Wireless · Discharging · Full. */
    val chargingStateLabel: String
        get() = when {
            battery == null -> "Reading battery…"
            battery.isFull -> "Full"
            battery.isCharging -> when (battery.source) {
                ChargeSource.AC -> "Charging on AC"
                ChargeSource.USB -> "Charging on USB"
                ChargeSource.WIRELESS -> "Charging on Wireless"
                ChargeSource.DOCK -> "Charging on Dock"
                else -> "Charging"
            }
            else -> "Discharging"
        }

    /** The insights strip (§8) only appears when the device reports something. */
    val hasBatteryInsights: Boolean
        get() {
            val r = battery ?: return false
            return r.cycleCount != null || r.chargeCounterUah != null || r.currentMicroAmp != null
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
        // observeAll (newest first) makes recents/discover reactive to imports;
        // the active profile's media is resolved from it — no extra query.
        db.mediaItemDao().observeAll(),
    ) { reading, s, profile, library ->
        val recent = library.take(RECENT_LIMIT)
        val recentCategories = recent.map { it.category }.toSet()
        // Deterministic "Discover" mix (§7): newest items from categories the
        // recents row does not already show; falls back to items outside the
        // recents list. Never fabricated — empty stays hidden in the UI.
        val discover = library
            .filter { it.category !in recentCategories }
            .take(DISCOVER_LIMIT)
            .ifEmpty {
                val recentIds = recent.map { it.id }.toSet()
                library.filter { it.id !in recentIds }.take(DISCOVER_LIMIT)
            }
        HomeUiState(
            battery = reading,
            settings = s,
            activeProfile = profile,
            activeProfileMedia = profile?.let { p -> library.firstOrNull { it.id == p.mediaItemId } },
            library = library,
            recent = recent,
            discover = discover,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    // v2/S2: setActiveProfile was removed — it had no caller (dead code, D17).
    // Profile activation stays in Studio, where the user picks the profile.

    private companion object {
        const val RECENT_LIMIT = 6
        const val DISCOVER_LIMIT = 6
    }
}
