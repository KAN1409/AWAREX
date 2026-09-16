package com.kareem.awarex.data

import android.content.Context
import com.kareem.awarex.core.model.InsightCard

class AwarePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun visibleInsights(insights: List<InsightCard>, now: Long): List<InsightCard> {
        val dismissed = prefs.getStringSet(KEY_DISMISSED_INSIGHTS, emptySet()).orEmpty().toSet()
        return insights.filter { insight ->
            insight.id !in dismissed && prefs.getLong(snoozeKey(insight.id), 0L) <= now
        }
    }

    fun dismissInsight(id: String) {
        val current = prefs.getStringSet(KEY_DISMISSED_INSIGHTS, emptySet()).orEmpty().toMutableSet()
        current += id
        prefs.edit().putStringSet(KEY_DISMISSED_INSIGHTS, current).remove(snoozeKey(id)).apply()
    }

    fun snoozeInsight(id: String, until: Long) {
        prefs.edit().putLong(snoozeKey(id), until).apply()
    }

    fun restoreAllInsights() {
        val editor = prefs.edit().remove(KEY_DISMISSED_INSIGHTS)
        prefs.all.keys.filter { it.startsWith(PREFIX_SNOOZE) }.forEach(editor::remove)
        editor.apply()
    }

    fun proactiveEnabled(): Boolean = prefs.getBoolean(KEY_PROACTIVE_ENABLED, true)

    fun setProactiveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROACTIVE_ENABLED, enabled).apply()
    }

    fun shouldNotify(key: String, now: Long, cooldownMillis: Long = NOTIFICATION_COOLDOWN_MILLIS): Boolean {
        val last = prefs.getLong(notificationKey(key), 0L)
        return last <= 0L || now - last >= cooldownMillis
    }

    fun markNotified(key: String, now: Long) {
        prefs.edit().putLong(notificationKey(key), now).apply()
    }

    private fun snoozeKey(id: String) = "$PREFIX_SNOOZE$id"
    private fun notificationKey(id: String) = "$PREFIX_NOTIFIED$id"

    companion object {
        const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val NOTIFICATION_COOLDOWN_MILLIS = 6 * 60 * 60 * 1000L
        private const val PREFS_NAME = "awarex_preferences"
        private const val KEY_DISMISSED_INSIGHTS = "dismissed_insights"
        private const val KEY_PROACTIVE_ENABLED = "proactive_enabled"
        private const val PREFIX_SNOOZE = "snooze:"
        private const val PREFIX_NOTIFIED = "notified:"
    }
}
