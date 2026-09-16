package com.kareem.awarex.background

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kareem.awarex.AwarexApplication
import com.kareem.awarex.MainActivity
import com.kareem.awarex.data.AwarePreferences
import com.kareem.awarex.data.AwareRepository
import com.kareem.awarex.data.AwareStore
import com.kareem.awarex.domain.ProactiveNotificationSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AwarenessWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preferences = AwarePreferences(applicationContext)
        if (!preferences.proactiveEnabled()) return@withContext Result.success()
        if (!canPostNotifications(applicationContext)) return@withContext Result.success()

        val repository = AwareRepository(AwareStore(applicationContext))
        try {
            val now = System.currentTimeMillis()
            val attention = repository.attentionCards()
            val world = repository.worldSnapshot()
            val visibleInsights = preferences.visibleInsights(world.insights, now)
            val candidate = ProactiveNotificationSelector.select(attention, visibleInsights)
                ?: return@withContext Result.success()
            if (!preferences.shouldNotify(candidate.key, now)) return@withContext Result.success()

            val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                1001,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(
                applicationContext,
                AwarexApplication.PROACTIVE_CHANNEL_ID
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(candidate.title)
                .setContentText(candidate.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(candidate.body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(applicationContext).notify(
                candidate.key.hashCode(),
                notification
            )
            preferences.markNotified(candidate.key, now)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            repository.close()
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
