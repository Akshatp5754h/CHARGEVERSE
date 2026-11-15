package com.akshat.chargeverse.ui.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.battery.BatteryReading
import com.akshat.chargeverse.battery.BatteryRepository
import com.akshat.chargeverse.battery.ChargingEvent
import com.akshat.chargeverse.core.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** ChargingViewModel for the full-screen experience. */
class ChargingViewModel : ViewModel() {

    private val battery: BatteryRepository = ServiceLocator.battery

    val state: StateFlow<BatteryReading?> = battery.reading
        .stateIn(viewModelScope, SharingStarted.Eagerly, battery.reading.value)

    val events = battery.events
}
