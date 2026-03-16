package com.subwayalert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SubwayAlertApp : Application() {

    companion object {
        const val CHANNEL_ID = "subway_alert_service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "地铁到站提醒",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "持续监听地铁报站语音"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
