package com.akshat.chargeverse.charging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.akshat.chargeverse.R

/** Notification channel holder for the charging experience. */
object ChargeverseNotification {
    const val CHANNEL = "charging_experience"

    fun ensure(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.channel_charging_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }
}
