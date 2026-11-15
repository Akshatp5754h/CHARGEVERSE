package com.akshat.chargeverse.battery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Direction of a charging state change. */
enum class ChargingEvent { CONNECTED, DISCONNECTED, FULL, LOW }

/**
 * Single source of truth for battery state across the app.
 * Emits live readings and one-shot charging transition events.
 *
 * The low-battery threshold follows the user's setting (DataStore
 * `low_battery_threshold`, default [DEFAULT_LOW_THRESHOLD]): the latest value
 * is re-read on every evaluation, so changing it in Settings takes effect
 * without a restart.
 */
class BatteryRepository(
    private val reader: BatteryReader,
    lowThresholdFlow: Flow<Int>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _lowThreshold = MutableStateFlow(DEFAULT_LOW_THRESHOLD)

    /** Current effective low-battery threshold in percent. */
    val lowThreshold: StateFlow<Int> = _lowThreshold.asStateFlow()

    private val _reading = MutableStateFlow(reader.readOnce())
    val reading: StateFlow<BatteryReading> = _reading.asStateFlow()

    private val _events = MutableSharedFlow<ChargingEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ChargingEvent> = _events.asSharedFlow()

    private var wasCharging = _reading.value.isCharging
    private var wasFull = _reading.value.isFull
    private var lowAlerted = false

    init {
        // Track user threshold changes (sanity-clamped to 1..99).
        lowThresholdFlow
            .onEach { pct -> _lowThreshold.value = pct.coerceIn(1, 99) }
            .launchIn(scope)
    }

    fun collectInto(flow: Flow<BatteryReading>): Flow<BatteryReading> = flow

    /**
     * Feed a fresh reading (from [BatteryReader.observe]) through transition
     * detection. Returns the reading for pipeline chaining.
     */
    suspend fun onNewReading(reading: BatteryReading): BatteryReading {
        _reading.value = reading
        if (reading.levelPercent >= 0) {
            val threshold = _lowThreshold.value
            if (BatteryMath.transitioned(wasCharging, reading.isCharging)) {
                _events.tryEmit(if (reading.isCharging) ChargingEvent.CONNECTED else ChargingEvent.DISCONNECTED)
                wasCharging = reading.isCharging
            }
            if (reading.isFull && !wasFull) _events.tryEmit(ChargingEvent.FULL)
            wasFull = reading.isFull
            if (reading.levelPercent <= threshold && !reading.isCharging && !lowAlerted) {
                lowAlerted = true
                _events.tryEmit(ChargingEvent.LOW)
            }
            if (reading.levelPercent > threshold + 5) lowAlerted = false
        }
        return reading
    }

    companion object {
        const val DEFAULT_LOW_THRESHOLD = 15
    }
}
