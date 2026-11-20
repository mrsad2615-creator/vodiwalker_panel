package com.mrsad.ambient.display

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mrsad.ambient.R

class AmbientActivationService : Service() {

    override fun onCreate() {
        super.onCreate()
        val channelId = "mrsad_ambient_channel"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "MRSAD Ambient Standby",
            NotificationManager.IMPORTANCE_MIN
        )
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.ambient_active))
            .setSmallIcon(R.drawable.ic_mrsad_logo)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
