package com.akshat.chargeverse.battery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Direction of a charging state change. */
enum class ChargingEvent { CONNECTED, DISCONNECTED, FULL, LOW }

/**
 * Single source of truth for battery state across the app.
 * Emits live readings and one-shot charging transition events.
 */
class BatteryRepository(private val reader: BatteryReader) {

    private val _reading = MutableStateFlow(reader.readOnce())
    val reading: StateFlow<BatteryReading> = _reading.asStateFlow()

    private val _events = MutableSharedFlow<ChargingEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ChargingEvent> = _events.asSharedFlow()

    private var wasCharging = _reading.value.isCharging
    private var wasFull = _reading.value.isFull
    private var lowAlerted = false

    fun collectInto(flow: Flow<BatteryReading>): Flow<BatteryReading> = flow

    /**
     * Feed a fresh reading (from [BatteryReader.observe]) through transition
     * detection. Returns the reading for pipeline chaining.
     */
    suspend fun onNewReading(reading: BatteryReading): BatteryReading {
        _reading.value = reading
        if (reading.levelPercent >= 0) {
            if (BatteryMath.transitioned(wasCharging, reading.isCharging)) {
                _events.tryEmit(if (reading.isCharging) ChargingEvent.CONNECTED else ChargingEvent.DISCONNECTED)
                wasCharging = reading.isCharging
            }
            if (reading.isFull && !wasFull) _events.tryEmit(ChargingEvent.FULL)
            wasFull = reading.isFull
            if (reading.levelPercent <= LOW_THRESHOLD && !reading.isCharging && !lowAlerted) {
                lowAlerted = true
                _events.tryEmit(ChargingEvent.LOW)
            }
            if (reading.levelPercent > LOW_THRESHOLD + 5) lowAlerted = false
        }
        return reading
    }

    companion object {
        const val LOW_THRESHOLD = 15
    }
}
