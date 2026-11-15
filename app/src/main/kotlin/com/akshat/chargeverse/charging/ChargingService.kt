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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        }.onFailure { Log.e(TAG, "startForeground failed", it) }

        // Update the notification as battery changes; stop when unplugged/full handled by receiver.
        ServiceLocator.battery.reading
            .onEach { reading ->
                if (!reading.isCharging) {
                    stopSelf()
                } else if (reading.levelPercent >= 0) {
                    updateNotification(reading.levelPercent)
                }
            }
            .launchIn(scope)
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, ChargingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, ChargeverseNotification.CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.charging_notif_title, ""))
            .setContentText(getString(R.string.charging_notif_text))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification(percent: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NOTIF_ID,
            NotificationCompat.Builder(this, ChargeverseNotification.CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.charging_notif_title, "$percent%"))
                .setContentText(getString(R.string.charging_notif_text))
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0,
                        Intent(this, ChargingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .build()
        )
    }

    companion object {
        const val TAG = "ChargingService"
        const val NOTIF_ID = 4021

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ChargingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChargingService::class.java))
        }
    }
}
