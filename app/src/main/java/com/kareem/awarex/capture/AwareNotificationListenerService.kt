package com.kareem.awarex.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kareem.awarex.data.AwareRepository
import com.kareem.awarex.data.AwareStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AwareNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingCaptures = ConcurrentHashMap<String, Job>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (posted.packageName == packageName) return

        val notification = posted.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (isOperationalNoise(notification)) return

        val extras = notification.extras
        val payload = NotificationEvidenceExtractor.Payload(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.map(CharSequence::toString)
                .orEmpty()
        )
        val captureKey = posted.key ?: "${posted.packageName}:${posted.id}:${posted.tag.orEmpty()}"

        pendingCaptures.remove(captureKey)?.cancel()
        val job = serviceScope.launch {
            delay(COALESCE_DELAY_MILLIS)
            val evidence = NotificationEvidenceExtractor.extract(payload) ?: return@launch
            val repository = AwareRepository(AwareStore(applicationContext))
            try {
                repository.captureIfNew(
                    text = evidence,
                    source = "notification:${posted.packageName}",
                    dedupeWindowMillis = NOTIFICATION_DEDUPE_WINDOW_MILLIS
                )
            } finally {
                repository.close()
                pendingCaptures.remove(captureKey)
            }
        }
        pendingCaptures[captureKey] = job
    }

    private fun isOperationalNoise(notification: Notification): Boolean {
        val category = notification.category
        if (category in NOISE_CATEGORIES) return true

        val extras = notification.extras
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        if (progressMax > 0) return true

        return notification.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
            category != Notification.CATEGORY_MESSAGE &&
            category != Notification.CATEGORY_EMAIL
    }

    override fun onDestroy() {
        pendingCaptures.values.forEach(Job::cancel)
        pendingCaptures.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val COALESCE_DELAY_MILLIS = 900L
        private const val NOTIFICATION_DEDUPE_WINDOW_MILLIS = 60_000L

        private val NOISE_CATEGORIES = setOf(
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_STATUS,
            Notification.CATEGORY_TRANSPORT
        )
    }
}
