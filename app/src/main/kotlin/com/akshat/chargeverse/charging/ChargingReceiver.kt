package com.akshat.chargeverse.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.akshat.chargeverse.core.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reacts to protected system broadcasts POWER_CONNECTED / POWER_DISCONNECTED.
 * Starts the charging experience (foreground service + full-screen intent)
 * when the user enabled it, and plays the connect/disconnect sound.
 */
class ChargingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val connected = intent.action == Intent.ACTION_POWER_CONNECTED
        val pendingResult = goAsync()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val settings = runCatching { ServiceLocator.settings.current() }.getOrNull()
                if (settings != null) {
                    if (connected) {
                        // Sound feedback regardless of the full-screen experience setting.
                        if (!settings.masterMuted && settings.connectSoundId > 0) {
                            ServiceLocator.db.soundItemDao().byId(settings.connectSoundId)?.let { item ->
                                ServiceLocator.soundEngine.muted = settings.masterMuted
                                ServiceLocator.soundEngine.volume = settings.soundVolume
                                ServiceLocator.soundEngine.play(android.net.Uri.parse(item.uri))
                            }
                        }
                        if (settings.showOnChargerConnect) {
                            runCatching { ChargingService.start(context) }
                                .onFailure { Log.w(TAG, "FGS start failed: ${it.message}") }
                        }
                    } else {
                        if (!settings.masterMuted && settings.disconnectSoundId > 0) {
                            ServiceLocator.db.soundItemDao().byId(settings.disconnectSoundId)?.let { item ->
                                ServiceLocator.soundEngine.play(android.net.Uri.parse(item.uri))
                            }
                        }
                        ChargingService.stop(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ChargingReceiver"
    }
}
