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
        val evidence = NotificationEvidenceExtractor.extract(
            NotificationEvidenceExtractor.Payload(
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
                text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                    ?.map(CharSequence::toString)
                    .orEmpty()
            )
        ) ?: return

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
