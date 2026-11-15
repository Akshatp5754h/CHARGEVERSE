package com.akshat.chargeverse.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Re-arms periodic battery automation work after a device reboot.
 * (Charger connect/disconnect events are manifest-declared broadcasts and
 * keep working across reboots without any re-registration.)
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching {
            AutomationWorkManager.schedule(context)
        }.onFailure { Log.w(TAG, "post-boot scheduling failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}

/** WorkManager wrapper for periodic battery automation checks. */
object AutomationWorkManager {
    private const val WORK_NAME = "chargeverse_battery_check"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BatteryCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
