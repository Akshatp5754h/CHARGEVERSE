package com.akshat.chargeverse.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reads genuine battery data through Android's BatteryManager and the sticky
 * ACTION_BATTERY_CHANGED broadcast. All unavailable readings stay null.
 */
class BatteryReader(private val context: Context) {

    fun readOnce(): BatteryReading = readStickyIntent()

    /**
     * Live readings driven by the system battery broadcast (fires on real
     * battery changes). Conflated so slow consumers get the latest value.
     */
    fun observe(): Flow<BatteryReading> = callbackFlow {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let { trySend(readIntent(it)) }
            }
        }
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { trySend(readIntent(it)) }
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.conflate().distinctUntilChanged()

    private fun readStickyIntent(): BatteryReading {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return if (intent != null) readIntent(intent) else fallbackReading()
    }

    private fun fallbackReading() = BatteryReading(
        levelPercent = -1, status = BatteryManager.BATTERY_STATUS_UNKNOWN,
        isCharging = false, isFull = false, source = ChargeSource.NONE,
        health = BatteryManager.BATTERY_HEALTH_UNKNOWN, temperatureC = null,
        voltageMv = null, currentMicroAmp = null, currentAverageMicroAmp = null,
        chargeCounterUah = null, technology = null, cycleCount = null,
        chargingPowerMw = null, minutesToFull = null, timestampMs = System.currentTimeMillis(),
    )

    private fun readIntent(intent: Intent): BatteryReading {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val isFull = status == BatteryManager.BATTERY_STATUS_FULL

        val source = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> ChargeSource.AC
            BatteryManager.BATTERY_PLUGGED_USB -> ChargeSource.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeSource.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> ChargeSource.DOCK
            0 -> ChargeSource.NONE
            else -> ChargeSource.OTHER
        }

        val temperatureRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val temperature = if (temperatureRaw == Int.MIN_VALUE) null else temperatureRaw / 10f

        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
        val voltageMv = if (voltage == Int.MIN_VALUE || voltage <= 0) null else voltage

        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)

        // Dynamic properties — may return Int.MIN_VALUE when unsupported.
        val currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAvg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentNowN = currentNow.takeIf { it != Int.MIN_VALUE && it != 0 }
        val currentAvgN = currentAvg.takeIf { it != Int.MIN_VALUE && it != 0 }
        val chargeCounterN = chargeCounter.takeIf { it != Int.MIN_VALUE && it > 0 }

        val cycleExtra = intent.getIntExtra(CYCLE_COUNT_EXTRA, -1)
        val cycleCount = cycleExtra.takeIf { it >= 0 }

        val designCapacityUah = designCapacityUah()
        val powerMw = BatteryMath.chargingPowerMw(voltageMv, currentNowN, isCharging)
        val eta = BatteryMath.minutesToFull(chargeCounterN, designCapacityUah, currentNowN, isCharging)

        return BatteryReading(
            levelPercent = percent,
            status = status,
            isCharging = isCharging,
            isFull = isFull,
            source = source,
            health = health,
            temperatureC = temperature,
            voltageMv = voltageMv,
            currentMicroAmp = currentNowN,
            currentAverageMicroAmp = currentAvgN,
            chargeCounterUah = chargeCounterN,
            technology = technology,
            cycleCount = cycleCount,
            chargingPowerMw = powerMw,
            minutesToFull = eta,
            timestampMs = System.currentTimeMillis(),
        )
    }

    /**
     * Design capacity in µAh when the device exposes a readable
     * /sys/class/power_supply/battery node. Null when not available —
     * CHARGEVERSE never fabricates this value.
     */
    private fun designCapacityUah(): Long? {
        for (path in listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design",
        )) {
            runCatching {
                val text = java.io.File(path).readText().trim().toLongOrNull()
                if (text != null && text > 0) return text
            }
        }
        return null
    }

    private companion object {
        /** Some OEMs report cycle count under this key in ACTION_BATTERY_CHANGED. */
        const val CYCLE_COUNT_EXTRA = "cycle_count"
    }
}
