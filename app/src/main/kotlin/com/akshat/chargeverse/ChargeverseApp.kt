package com.akshat.chargeverse

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.akshat.chargeverse.core.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class ChargeverseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        createNotificationChannels()
        startBatteryAndAutomation()
    }

    /** Continuous battery feed + automation engine for the whole process. */
    private fun startBatteryAndAutomation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            ServiceLocator.batteryReader.observe()
                .collect { ServiceLocator.battery.onNewReading(it) }
        }
        ServiceLocator.automation.start()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHARGING,
                getString(R.string.channel_charging_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.channel_charging_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(R.string.channel_alerts_desc) }
        )
    }

    companion object {
        const val CHANNEL_CHARGING = "charging_experience"
        const val CHANNEL_ALERTS = "battery_alerts"
    }
}
