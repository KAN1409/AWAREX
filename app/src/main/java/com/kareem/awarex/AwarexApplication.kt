package com.kareem.awarex

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.kareem.awarex.background.AwarenessScheduler

class AwarexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AwarenessScheduler.schedule(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            PROACTIVE_CHANNEL_ID,
            "AWAREX attention",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "High-value reminders and discoveries AWAREX thinks deserve attention."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val PROACTIVE_CHANNEL_ID = "awarex_attention"
    }
}
