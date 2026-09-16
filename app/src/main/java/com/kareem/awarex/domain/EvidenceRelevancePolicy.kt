package com.kareem.awarex.domain

import java.util.Locale

/**
 * Turns the Android notification stream into evidence candidates instead of mirroring the
 * notification shade. Manual evidence is always accepted. Passive evidence must either come
 * from a real conversation channel or contain a durable work/life signal worth remembering.
 */
object EvidenceRelevancePolicy {
    fun shouldPersist(text: String, source: String): Boolean {
        val clean = normalize(text)
        if (clean.isEmpty()) return false
        if (!source.startsWith("notification:")) return true

        val packageName = source.removePrefix("notification:").trim()
        if (packageName in ALWAYS_IGNORED_PACKAGES) return false
        if (looksLikeOperationalNoise(clean)) return false
        if (looksLikeContextFeed(clean)) return false
        if (looksLikeSocialActivityNoise(clean)) return false

        if (packageName in CONVERSATION_PACKAGES) return true

        // Email and unknown apps are not evidence merely because they posted a notification.
        // Keep them only when the text itself contains a durable action/change/commitment signal.
        return containsDurableSignal(clean)
    }

    fun shouldSurface(text: String, source: String): Boolean = shouldPersist(text, source)

    private fun looksLikeOperationalNoise(text: String): Boolean {
        if (NOISE_PHRASES.any(text::contains)) return true
        if (TRANSFER_PROGRESS.containsMatchIn(text)) return true
        if (MORE_NOTIFICATIONS.matches(text)) return true
        return false
    }

    private fun looksLikeContextFeed(text: String): Boolean {
        if ("see full forecast" in text) return true
        if (WEATHER_FEED.containsMatchIn(text)) return true
        if (NEWS_DISCOVER_HINTS.any(text::contains)) return true
        return false
    }

    private fun looksLikeSocialActivityNoise(text: String): Boolean {
        return SOCIAL_ACTIVITY_PHRASES.any(text::contains) ||
            BIRTHDAY_DIGEST.containsMatchIn(text)
    }

    private fun containsDurableSignal(text: String): Boolean {
        return DURABLE_SIGNAL_PHRASES.any(text::contains) ||
            DURABLE_TIME_SIGNAL.containsMatchIn(text) ||
            ARABIC_DURABLE_SIGNAL.containsMatchIn(text)
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()

    private val ALWAYS_IGNORED_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.providers.downloads",
        "com.android.providers.downloads.ui",
        "com.samsung.android.app.smartcapture",
        "com.sec.android.daemonapp",
        "com.google.android.googlequicksearchbox",
        "com.facebook.katana",
        "com.snapchat.android",
        "com.openai.chatgpt"
    )

    private val CONVERSATION_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.facebook.orca"
    )

    private val NOISE_PHRASES = listOf(
        "screenshot saved",
        "tap here to see your screenshot",
        "download complete",
        "download completed",
        "downloading...",
        "downloading…",
        "waiting for network",
        "download paused",
        "preparing download",
        "updating messages",
        "syncing messages",
        "checking for new messages",
        "connecting...",
        "connecting…",
        "running...",
        "running…",
        "running in the background",
        "background service",
        "response ready: tap to return"
    )

    private val SOCIAL_ACTIVITY_PHRASES = listOf(
        "added a snap to their story",
        "added snaps to their story",
        "added to their story",
        "sent you a snap",
        "invited you to their channel",
        "close friend updates",
        "people you may know",
        "friend suggestion",
        "suggested for you",
        "waiting for you:"
    )

    private val NEWS_DISCOVER_HINTS = listOf(
        "see full story",
        "top stories",
        "recommended stories",
        "news for you"
    )

    private val DURABLE_SIGNAL_PHRASES = listOf(
        "i'll send",
        "i will send",
        "will send",
        "please send",
        "sent the",
        "has been sent",
        "received the",
        "has been received",
        "waiting for",
        "still waiting",
        "revised quotation",
        "revised quote",
        "quotation",
        "purchase order",
        "approval",
        "approved",
        "invoice",
        "deadline",
        "due date",
        "appointment",
        "meeting moved",
        "meeting changed",
        "rescheduled",
        "cancelled",
        "confirmed",
        "delivery date",
        "attached",
        "price changed",
        "amount changed",
        "contract",
        "tender"
    )

    private val TRANSFER_PROGRESS = Regex(
        """\b\d+(?:\.\d+)?\s*(?:kb|mb|gb)\s*/\s*(?:\?|\d+(?:\.\d+)?\s*(?:kb|mb|gb)?)""",
        RegexOption.IGNORE_CASE
    )

    private val MORE_NOTIFICATIONS = Regex(
        """^\d+\s+more\s+notifications?$""",
        RegexOption.IGNORE_CASE
    )

    private val WEATHER_FEED = Regex(
        """\b\d{1,3}\s*°\s*(?:c|f)?\b.*\b(?:clear|cloudy|rain|rainy|sunny|forecast|storm|snow|windy|weather)\b""",
        RegexOption.IGNORE_CASE
    )

    private val BIRTHDAY_DIGEST = Regex(
        """\b(?:happy birthday|birthdays? today|have birthdays?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val DURABLE_TIME_SIGNAL = Regex(
        """\b(?:today|tomorrow|tonight|this morning|this afternoon|this evening|by\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val ARABIC_DURABLE_SIGNAL = Regex(
        """(?:هبعت|هأبعت|هارسِل|هرسل|ابعت|أبعت|ارسل|أرسل|تم\s+الارسال|تم\s+الإرسال|استلمت|وصل|مستني|منتظر|بكره|بكرة|غد[ًاا]?|موعد|عرض\s+سعر|موافقة|اعتماد|فاتورة|تسليم|اتأجل|تأجل|اتلغى|إلغاء|الغاء)"""
    )
}
