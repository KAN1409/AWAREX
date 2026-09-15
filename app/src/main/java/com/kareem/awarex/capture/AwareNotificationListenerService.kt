package com.kareem.awarex.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.kareem.awarex.data.AwareRepository
import com.kareem.awarex.data.AwareStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AwareNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (posted.packageName == packageName) return

        val notification = posted.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val normalText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString().trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" · ")
            .orEmpty()

        val body = sequenceOf(bigText, normalText, lines).firstOrNull { it.isNotEmpty() }.orEmpty()
        if (body.isEmpty() && title.isEmpty()) return

        val evidence = when {
            title.isNotEmpty() && body.isNotEmpty() && !body.startsWith(title, ignoreCase = true) -> "$title: $body"
            body.isNotEmpty() -> body
            else -> title
        }.trim()

        if (evidence.isEmpty()) return

        serviceScope.launch {
            val repository = AwareRepository(AwareStore(applicationContext))
            try {
                repository.captureIfNew(
                    text = evidence,
                    source = "notification:${posted.packageName}"
                )
            } finally {
                repository.close()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
