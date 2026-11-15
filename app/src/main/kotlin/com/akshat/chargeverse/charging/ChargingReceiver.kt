package com.akshat.chargeverse.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.akshat.chargeverse.core.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reacts to protected system broadcasts POWER_CONNECTED / POWER_DISCONNECTED.
 * Starts the charging experience (foreground service + full-screen intent)
 * when the user enabled it, plays the connect/disconnect sound and (when the
 * haptics setting is on) one short vibration on connect.
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
                        // D6: one short vibration on charger connect when enabled.
                        if (settings.hapticsEnabled) vibrateOnce(context)
                        if (settings.showOnChargerConnect) {
                            runCatching { ChargingService.start(context) }
                                .onFailure { e ->
                                    Log.w(TAG, "FGS start failed: ${e.message}")
                                    // D15: keep the experience reachable even when the
                                    // FGS is rejected (Android 12+ restrictions).
                                    ChargingService.postFallbackNotification(context)
                                }
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

    /** One short vibration via VibratorManager (API 31+) or the legacy Vibrator. */
    private fun vibrateOnce(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }.onFailure { Log.w(TAG, "haptic feedback failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "ChargingReceiver"
    }
}
