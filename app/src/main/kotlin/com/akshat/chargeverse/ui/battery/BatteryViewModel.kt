package com.akshat.chargeverse.ui.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akshat.chargeverse.battery.BatteryReading
import com.akshat.chargeverse.core.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BatteryViewModel : ViewModel() {
    private val battery = ServiceLocator.battery

    val reading: StateFlow<BatteryReading?> = battery.reading
        .stateIn(viewModelScope, SharingStarted.Eagerly, battery.reading.value)
}
