package com.akshat.chargeverse.charging

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.akshat.chargeverse.R
import com.akshat.chargeverse.core.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service running while the charger is connected. It owns the
 * charging-experience notification (full-screen intent) and keeps the
 * experience alive while plugged in. Type = specialUse (rendering the user's
 * charging animation while the device charges).
 */
class ChargingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The single battery-flow collector; cancelled/replaced on every start. */
    private var collectJob: Job? = null

    /** Loop-sound state survives re-starts so a running loop is not doubled. */
    private var loopStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        runCatching {
            when {
                Build.VERSION.SDK_INT >= 34 ->
                    startForeground(
                        NOTIF_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    )
                else -> startForeground(NOTIF_ID, notification)
            }
        }.onFailure { Log.e(TAG, "startForeground failed", it) }

        // Update the notification as battery changes; stop when unplugged/full handled by receiver.
        // Never stack collectors: cancel the previous one first.
        collectJob?.cancel()
        collectJob = ServiceLocator.battery.reading
            .onEach { reading ->
                val s = runCatching { ServiceLocator.settings.current() }.getOrNull() ?: return@onEach
                ServiceLocator.soundEngine.muted = s.masterMuted
                ServiceLocator.soundEngine.volume = s.soundVolume

                if (!reading.isCharging) {
                    if (loopStarted) {
                        ServiceLocator.soundEngine.stop()
                        loopStarted = false
                    }
                    stopSelf()
                    return@onEach
                }

                // Charging sound loop while charging, if enabled. loopStarted is a
                // service field: when the service is already running (repeat
                // onStartCommand), the live loop is kept instead of re-started.
                if (s.repeatChargingSound && s.connectSoundId > 0 && !loopStarted) {
                    ServiceLocator.db.soundItemDao().byId(s.connectSoundId)?.let { item ->
                        loopStarted = true
                        ServiceLocator.soundEngine.play(android.net.Uri.parse(item.uri), loop = true)
                    }
                }

                if (reading.levelPercent >= 0) {
                    updateNotification(reading.levelPercent)
                }
            }
            .launchIn(scope)
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        collectJob = null
        loopStarted = false
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val percent = ServiceLocator.battery.reading.value.levelPercent
        return chargingNotification(
            percentText = if (percent >= 0) "$percent%" else "…",
            withFullScreenIntent = true,
        )
    }

    private fun updateNotification(percent: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, chargingNotification(percentText = "$percent%", withFullScreenIntent = false))
    }

    /** One builder for initial + updated + fallback notifications. */
    private fun chargingNotification(percentText: String, withFullScreenIntent: Boolean): Notification {
        val open = openActivityIntent()
        val builder = NotificationCompat.Builder(this, ChargeverseNotification.CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            // "Charging — <battery>%" — never renders with an empty arg (D7/D11).
            .setContentTitle(getString(R.string.charging_notif_title, percentText))
            .setContentText(getString(R.string.charging_notif_text))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(R.drawable.ic_nav_home, getString(R.string.notif_action_open), open)
            .addAction(
                R.drawable.ic_util_close,
                getString(R.string.notif_action_stop),
                stopServiceIntent(),
            )
        if (withFullScreenIntent) builder.setFullScreenIntent(open, true)
        return builder.build()
    }

    private fun openActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, ChargingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopServiceIntent(): PendingIntent = PendingIntent.getService(
        this, 1,
        Intent(this, ChargingService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val TAG = "ChargingService"
        const val NOTIF_ID = 4021
        const val ACTION_STOP = "com.akshat.chargeverse.charging.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ChargingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChargingService::class.java))
        }

        /**
         * D15 fallback: when the FGS cannot start (SecurityException /
         * ForegroundServiceStartNotAllowedException on Android 12+), post a
         * normal notification that opens the charging screen on tap — never
         * crash, never swallow silently.
         */
        fun postFallbackNotification(context: Context) {
            runCatching {
                ChargeverseNotification.ensure(context)
                val appContext = context.applicationContext
                val open = PendingIntent.getActivity(
                    appContext, 0,
                    Intent(appContext, ChargingActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                // Best-effort battery label for the title; "…" when unknown.
                val percent = runCatching { ServiceLocator.battery.reading.value.levelPercent }.getOrDefault(-1)
                val notification = NotificationCompat.Builder(appContext, ChargeverseNotification.CHANNEL)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(
                        appContext.getString(
                            R.string.charging_notif_title,
                            if (percent >= 0) "$percent%" else "…",
                        )
                    )
                    .setContentText(appContext.getString(R.string.charging_notif_text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(FALLBACK_NOTIF_ID, notification)
            }.onFailure { Log.w(TAG, "fallback notification failed: ${it.message}") }
        }

        private const val FALLBACK_NOTIF_ID = 4022
    }
}
