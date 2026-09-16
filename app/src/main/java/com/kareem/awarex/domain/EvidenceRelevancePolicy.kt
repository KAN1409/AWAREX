package com.kareem.awarex.domain

import java.util.Locale

/**
 * Keeps passive capture focused on human/semantic evidence instead of Android UI chatter.
 * Manual evidence is always accepted; notification evidence is screened conservatively.
 */
object EvidenceRelevancePolicy {
    fun shouldPersist(text: String, source: String): Boolean {
        val clean = normalize(text)
        if (clean.isEmpty()) return false
        if (!source.startsWith("notification:")) return true
        return !looksLikeOperationalNoise(clean)
    }

    fun shouldSurface(text: String, source: String): Boolean = shouldPersist(text, source)

    private fun looksLikeOperationalNoise(text: String): Boolean {
        if (NOISE_PHRASES.any(text::contains)) return true
        if (TRANSFER_PROGRESS.containsMatchIn(text)) return true
        return false
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()

    private val NOISE_PHRASES = listOf(
        "screenshot saved",
        "tap here to see your screenshot",
        "download complete",
        "download completed",
        "downloading...",
        "downloading…",
        "waiting for network",
        "download paused",
        "preparing download"
    )

    private val TRANSFER_PROGRESS = Regex(
        """\b\d+(?:\.\d+)?\s*(?:kb|mb|gb)\s*/\s*(?:\?|\d+(?:\.\d+)?\s*(?:kb|mb|gb)?)""",
        RegexOption.IGNORE_CASE
    )
}
