package com.akshat.chargeverse.automation

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.akshat.chargeverse.battery.BatteryReader
import com.akshat.chargeverse.core.di.ServiceLocator

/**
 * Periodic battery sweep used when the app process is not alive: applies
 * percentage-triggered automation rules even without the dashboard open.
 */
class BatteryCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            ServiceLocator.init(applicationContext as android.app.Application)
            val reading = BatteryReader(applicationContext).readOnce()
            ServiceLocator.automation.onReadingForWorker(reading)
            Result.success()
        }.getOrElse {
            Log.w("BatteryCheckWorker", "battery check failed: ${it.message}")
            Result.retry()
        }
    }
}
