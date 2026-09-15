package com.kareem.awarex.domain

import com.kareem.awarex.core.model.OpenLoop
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

class CommitmentEngine(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    data class Candidate(
        val title: String,
        val normalizedSubject: String,
        val dueAt: Long?
    )

    fun extract(text: String, observedAt: Long): Candidate? {
        val normalized = normalize(text)
        if (!looksLikeCommitment(normalized)) return null

        val dueAt = when {
            containsAny(normalized, "tomorrow", "بكرة", "بكره") -> nextDayStart(observedAt)
            containsAny(normalized, "today", "النهاردة", "النهارده", "اليوم") -> endOfDay(observedAt)
            else -> null
        }
        val subjectTokens = meaningfulTokens(normalized)
        if (subjectTokens.isEmpty()) return null
        val subject = subjectTokens.take(8).joinToString(" ")
        val title = buildTitle(text, subject)
        return Candidate(title = title, normalizedSubject = subject, dueAt = dueAt)
    }

    fun matchingResolution(text: String, openLoops: List<OpenLoop>): OpenLoop? {
        val normalized = normalize(text)
        if (!looksLikeResolution(normalized)) return null
        val incoming = meaningfulTokens(normalized).toSet()
        if (incoming.isEmpty()) return null
        return openLoops
            .asSequence()
            .filterNot { it.isResolved }
            .map { loop -> loop to overlapScore(incoming, loop.normalizedSubject) }
            .filter { (_, score) -> score >= 0.34 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun looksLikeCommitment(text: String): Boolean = containsAny(
        text,
        "i'll ",
        "i will ",
        "we'll ",
        "we will ",
        "will send",
        "will share",
        "will review",
        "will prepare",
        "هبعت",
        "هابعت",
        "هنبعت",
        "هراجع",
        "هجهز",
        "هخلص",
        "هرد",
        "هعمل",
        "هبلغ"
    )

    private fun looksLikeResolution(text: String): Boolean = containsAny(
        text,
        "sent ",
        "sent the",
        "shared ",
        "attached ",
        "done",
        "completed",
        "received ",
        "finished ",
        "تم ",
        "اتبعت",
        "بعت ",
        "بعتلك",
        "خلصت",
        "جهزت",
        "وصل ",
        "مرفق"
    )

    private fun overlapScore(incoming: Set<String>, subject: String): Double {
        val target = meaningfulTokens(subject).toSet()
        if (target.isEmpty()) return 0.0
        return incoming.intersect(target).size.toDouble() / target.size.toDouble()
    }

    private fun buildTitle(original: String, subject: String): String {
        val clean = original.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= 72) clean else "Waiting for ${subject.take(52)}"
    }

    private fun nextDayStart(observedAt: Long): Long {
        val base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(observedAt), zoneId)
        return base.plusDays(1).truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()
    }

    private fun endOfDay(observedAt: Long): Long {
        val base = ZonedDateTime.ofInstant(Instant.ofEpochMilli(observedAt), zoneId)
        return base.toLocalDate().plusDays(1).atStartOfDay(zoneId).minusNanos(1).toInstant().toEpochMilli()
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("[^\\p{L}\\p{N}']+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun meaningfulTokens(value: String): List<String> {
        val stopWords = STOP_WORDS
        return normalize(value)
            .split(' ')
            .asSequence()
            .map { it.trim(''') }
            .filter { it.length >= 3 }
            .filterNot { it in stopWords }
            .distinct()
            .toList()
    }

    private fun containsAny(value: String, vararg needles: String): Boolean = needles.any(value::contains)

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "that", "this", "with", "for", "you", "your", "will", "i'll", "we'll",
            "tomorrow", "today", "send", "sent", "share", "shared", "review", "prepare", "done",
            "بكره", "بكرة", "النهاردة", "النهارده", "اليوم", "هبعت", "هابعت", "هنبعت", "هراجع",
            "هجهز", "هخلص", "هرد", "هعمل", "هبلغ", "بعت", "بعتلك", "تم", "خلصت", "جهزت"
        )
    }
}
