package com.akshat.chargeverse.battery

/**
 * A snapshot of genuine Android battery readings. Every field that the device
 * does not report stays `null` — CHARGEVERSE never fabricates measurements.
 */
data class BatteryReading(
    val levelPercent: Int,
    /** BatteryManager.BATTERY_STATUS_* */
    val status: Int,
    val isCharging: Boolean,
    val isFull: Boolean,
    /** None / AC / USB / Wireless / Dock / Other — from BatteryManager.EXTRA_PLUGGED. */
    val source: ChargeSource,
    /** BatteryManager.BATTERY_HEALTH_* */
    val health: Int,
    val temperatureC: Float?,
    val voltageMv: Int?,
    /** Microamps; negative when discharging. Null when device reports 0/invalid. */
    val currentMicroAmp: Int?,
    val currentAverageMicroAmp: Int?,
    /** Design charge counter in µAh, when the device exposes it. */
    val chargeCounterUah: Int?,
    /** Battery technology string e.g. "Li-poly". Null when not reported. */
    val technology: String?,
    /** Charge cycle count where supported (API 34+, device-dependent). */
    val cycleCount: Int?,
    /** Computed only from real voltage × current readings, in mW. */
    val chargingPowerMw: Int?,
    /** Only shown while charging and only computed from real values, in minutes. */
    val minutesToFull: Long?,
    val timestampMs: Long,
) {
    val statusLabel: String
        get() = when (status) {
            android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            android.os.BatteryManager.BATTERY_STATUS_FULL -> "Full"
            android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }

    val healthLabel: String
        get() = when (health) {
            android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            android.os.BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
}

enum class ChargeSource { NONE, AC, USB, WIRELESS, DOCK, OTHER }

/** Extracted pluggable logic for unit tests: compute charging power and ETA. */
object BatteryMath {

    /**
     * Charging power in mW from real voltage and current readings.
     * Returns null when either is missing or the device is not charging.
     *
     * Unit chain: mV × µA = 1e-9 W (nanowatt scale). To reach mW (1e-3 W):
     * divide by 1e6.
     */
    fun chargingPowerMw(voltageMv: Int?, currentMicroAmp: Int?, isCharging: Boolean): Int? {
        if (!isCharging) return null
        val v = voltageMv ?: return null
        val i = currentMicroAmp ?: return null
        if (v <= 0 || i <= 0) return null
        return ((v.toLong() * i.toLong()) / 1_000_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Estimated minutes to full, computed only from real charge counter and
     * current. Returns null when inputs are unusable.
     */
    fun minutesToFull(
        chargeCounterUah: Int?,
        designCapacityUah: Long?,
        currentMicroAmp: Int?,
        isCharging: Boolean,
    ): Long? {
        if (!isCharging) return null
        val counter = chargeCounterUah ?: return null
        val full = designCapacityUah ?: return null
        val current = currentMicroAmp ?: return null
        if (full <= 0 || counter <= 0 || current <= 0) return null
        val remainingUah = (full - counter).coerceAtLeast(0)
        return (remainingUah.toLong() * 60L / current.toLong())
    }

    /** Simple state classifier used by automation and UI. */
    fun transitioned(wasCharging: Boolean, isCharging: Boolean): Boolean = wasCharging != isCharging
}
