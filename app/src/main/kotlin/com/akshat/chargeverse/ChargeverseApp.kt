package com.akshat.chargeverse

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.akshat.chargeverse.core.di.ServiceLocator
import com.akshat.chargeverse.data.db.SoundItemEntity
import com.akshat.chargeverse.data.db.SoundRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

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
            seedBuiltInSounds()
        }
        scope.launch {
            ServiceLocator.batteryReader.observe()
                .collect { ServiceLocator.battery.onNewReading(it) }
        }
        ServiceLocator.automation.start()
    }

    /**
     * Registers the four original built-in chimes once, and assigns them as
     * default role sounds when the user has not chosen any.
     */
    private suspend fun seedBuiltInSounds() {
        val dao = ServiceLocator.db.soundItemDao()
        val existing = dao.observeAll().first()
        val builtIns = existing.filter { it.isBuiltIn }
        val toSeed = listOf(
            "connect" to SoundRole.CONNECT,
            "disconnect" to SoundRole.DISCONNECT,
            "full" to SoundRole.FULL,
            "low" to SoundRole.LOW,
        ).filter { (name, role) -> builtIns.none { it.role == role && it.name == name } }

        val ids = mutableMapOf<SoundRole, Long>()
        for ((name, role) in toSeed) {
            val id = dao.insert(
                SoundItemEntity(
                    name = name,
                    uri = "asset://sounds/$name.wav",
                    role = role,
                    isBuiltIn = true,
                    addedAtMs = System.currentTimeMillis(),
                )
            )
            ids[role] = id
        }
        // Keep originally-seeded ids for defaults below.
        if (ids.isEmpty()) {
            builtIns.forEach { if (ids[it.role] == null) ids[it.role] = it.id }
        }

        val settings = ServiceLocator.settings
        val s = settings.current()
        if (s.connectSoundId <= 0) ids[SoundRole.CONNECT]?.let { settings.setConnectSound(it) }
        if (s.disconnectSoundId <= 0) ids[SoundRole.DISCONNECT]?.let { settings.setDisconnectSound(it) }
        if (s.fullSoundId <= 0) ids[SoundRole.FULL]?.let { settings.setFullSound(it) }
        if (s.lowSoundId <= 0) ids[SoundRole.LOW]?.let { settings.setLowSound(it) }
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
