package com.kareem.awarex.domain

import java.util.Locale

/**
 * Keeps passive capture focused on durable human/semantic evidence instead of Android UI chatter.
 * Manual evidence is always accepted; notification evidence is screened conservatively.
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
        return true
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
        return false
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()

    private val ALWAYS_IGNORED_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.providers.downloads",
        "com.android.providers.downloads.ui",
        "com.samsung.android.app.smartcapture"
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
        "running in the background",
        "background service"
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
}
