package com.kareem.awarex.domain

import com.kareem.awarex.core.model.InsightCard
import com.kareem.awarex.core.model.InsightKind
import com.kareem.awarex.core.model.Observation
import com.kareem.awarex.core.model.OpenLoop
import com.kareem.awarex.core.model.SituationCard
import com.kareem.awarex.core.model.SituationState
import com.kareem.awarex.core.model.WorldSnapshot
import java.util.Locale
import kotlin.math.abs

/**
 * A small deterministic world model that turns durable evidence into evolving situations.
 * It deliberately does not depend on a cloud model: evidence remains useful when AI is offline.
 */
class WorldModelEngine {
    private data class PriceFact(
        val amount: Double,
        val currency: String,
        val topic: Set<String>,
        val observation: Observation,
        val entity: String?
    )

    fun build(
        observations: List<Observation>,
        loops: List<OpenLoop>,
        now: Long
    ): WorldSnapshot {
        val orderedEvidence = observations
            .distinctBy { it.id }
            .sortedWith(compareBy<Observation> { it.observedAt }.thenBy { it.id })
        val evidenceById = orderedEvidence.associateBy { it.id }
        val priceFacts = orderedEvidence.mapNotNull(::extractPriceFact)
        val priceClusters = clusterPriceFacts(priceFacts)

        val insights = mutableListOf<InsightCard>()
        val situations = mutableListOf<SituationCard>()
        val priceTopics = mutableListOf<Set<String>>()

        priceClusters.forEachIndexed { index, cluster ->
            val latest = cluster.maxBy { it.observation.observedAt }
            val topicTitle = humanizeTopic(latest.topic)
            val relatedLoops = loops.filter { sameTopic(latest.topic, topicTokens(it.normalizedSubject)) }
            val activeRelated = relatedLoops.firstOrNull { !it.isResolved }
            val latestResolved = relatedLoops
                .filter { it.isResolved }
                .maxByOrNull { it.resolvedAt ?: it.createdAt }
            val previous = cluster
                .asSequence()
                .filter { it.observation.id != latest.observation.id }
                .filter { !sameAmount(it.amount, latest.amount) }
                .maxByOrNull { it.observation.observedAt }

            val changed = previous != null
            val loopSuffix = when {
                activeRelated != null -> " A related commitment is still open."
                latestResolved != null -> " The related commitment was completed."
                else -> ""
            }
            val summary = if (previous != null) {
                val delta = percentChange(previous.amount, latest.amount)
                "Latest ${latest.currency} value moved from ${formatAmount(previous.amount)} to ${formatAmount(latest.amount)} (${formatPercent(delta)}).$loopSuffix"
            } else {
                "Latest known ${latest.currency} value is ${formatAmount(latest.amount)}.$loopSuffix"
            }

            situations += SituationCard(
                id = "price:${index}:${latest.topic.sorted().joinToString("-")}",
                title = topicTitle,
                state = if (changed) SituationState.CHANGED else when {
                    activeRelated != null -> SituationState.WAITING
                    latestResolved != null -> SituationState.RESOLVED
                    else -> SituationState.DEVELOPING
                },
                summary = summary,
                evidenceCount = cluster.size + relatedLoops.size.coerceAtMost(2),
                updatedAt = maxOf(
                    latest.observation.observedAt,
                    latestResolved?.resolvedAt ?: 0L,
                    activeRelated?.createdAt ?: 0L
                ),
                primaryEntity = latest.entity
            )
            priceTopics += latest.topic

            if (previous != null) {
                val delta = percentChange(previous.amount, latest.amount)
                val absoluteDelta = abs(delta)
                insights += InsightCard(
                    id = "price-change:${previous.observation.id}:${latest.observation.id}",
                    kind = InsightKind.CHANGE,
                    title = "$topicTitle changed",
                    body = "${previous.currency} ${formatAmount(previous.amount)} → ${latest.currency} ${formatAmount(latest.amount)} (${formatPercent(delta)})",
                    whyItMatters = if (absoluteDelta >= 5.0) {
                        "AWAREX linked two pieces of evidence about the same situation and found a material value change."
                    } else {
                        "AWAREX linked two pieces of evidence about the same situation and found a value change."
                    },
                    evidenceTexts = listOf(previous.observation.text, latest.observation.text),
                    confidence = if (latest.topic.size >= 2) 0.94 else 0.86,
                    priority = when {
                        absoluteDelta >= 10.0 -> 98
                        absoluteDelta >= 5.0 -> 92
                        else -> 80
                    },
                    createdAt = latest.observation.observedAt
                )
            }
        }

        loops
            .sortedByDescending { it.resolvedAt ?: it.createdAt }
            .forEach { loop ->
                val createdEvidence = evidenceById[loop.createdFromObservationId]
                val resolutionEvidence = loop.resolutionObservationId?.let(evidenceById::get)
                val subjectTokens = topicTokens(
                    buildString {
                        append(loop.normalizedSubject)
                        createdEvidence?.let { append(' ').append(it.text) }
                    }
                )
                if (priceTopics.any { sameTopic(it, subjectTokens) }) return@forEach

                val title = if (subjectTokens.isNotEmpty()) {
                    humanizeTopic(subjectTokens)
                } else {
                    loop.title.trim().replace(Regex("\\s+"), " ").take(72)
                }
                val updatedAt = loop.resolvedAt ?: loop.createdAt
                situations += SituationCard(
                    id = "loop:${loop.id}",
                    title = title,
                    state = if (loop.isResolved) SituationState.RESOLVED else SituationState.WAITING,
                    summary = if (loop.isResolved) {
                        "AWAREX matched completion evidence to the earlier commitment and closed the loop."
                    } else {
                        "A commitment is still open and AWAREX is tracking it."
                    },
                    evidenceCount = if (resolutionEvidence == null) 1 else 2,
                    updatedAt = updatedAt,
                    primaryEntity = createdEvidence?.let { extractEntity(it.text) }
                )

                if (
                    loop.isResolved &&
                    loop.resolvedAt != null &&
                    now - loop.resolvedAt in 0..COMPLETION_INSIGHT_WINDOW_MILLIS &&
                    createdEvidence != null &&
                    resolutionEvidence != null
                ) {
                    insights += InsightCard(
                        id = "completion:${loop.id}:${resolutionEvidence.id}",
                        kind = InsightKind.COMPLETION,
                        title = "$title completed",
                        body = "AWAREX matched new evidence to an earlier commitment and closed the loop.",
                        whyItMatters = "This no longer needs to stay in your working memory.",
                        evidenceTexts = listOf(createdEvidence.text, resolutionEvidence.text),
                        confidence = 0.91,
                        priority = 60,
                        createdAt = loop.resolvedAt
                    )
                }
            }

        return WorldSnapshot(
            insights = insights
                .distinctBy { it.id }
                .sortedWith(compareByDescending<InsightCard> { it.priority }.thenByDescending { it.createdAt })
                .take(MAX_INSIGHTS),
            situations = situations
                .distinctBy { it.id }
                .sortedByDescending { it.updatedAt }
                .take(MAX_SITUATIONS)
        )
    }

    private fun extractPriceFact(observation: Observation): PriceFact? {
        val match = AMOUNT_THEN_CURRENCY.find(observation.text)
            ?: CURRENCY_THEN_AMOUNT.find(observation.text)
            ?: return null
        val amountText: String
        val currencyText: String
        if (match.pattern == AMOUNT_THEN_CURRENCY.pattern) {
            amountText = match.groupValues[1]
            currencyText = match.groupValues[2]
        } else {
            currencyText = match.groupValues[1]
            amountText = match.groupValues[2]
        }
        val amount = amountText.replace(",", "").replace(" ", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null
        val entity = extractEntity(observation.text)
        val entityTokens = entity?.let(::topicTokens).orEmpty()
        val topic = topicTokens(observation.text) - entityTokens
        if (topic.isEmpty()) return null
        return PriceFact(
            amount = amount,
            currency = canonicalCurrency(currencyText),
            topic = topic,
            observation = observation,
            entity = entity
        )
    }

    private fun clusterPriceFacts(facts: List<PriceFact>): List<List<PriceFact>> {
        val clusters = mutableListOf<MutableList<PriceFact>>()
        facts.sortedBy { it.observation.observedAt }.forEach { fact ->
            val cluster = clusters.firstOrNull { existing ->
                existing.first().currency == fact.currency &&
                    existing.any { sameTopic(it.topic, fact.topic) }
            }
            if (cluster == null) clusters += mutableListOf(fact) else cluster += fact
        }
        return clusters
    }

    private fun sameTopic(left: Set<String>, right: Set<String>): Boolean {
        if (left.isEmpty() || right.isEmpty()) return false
        val overlap = left.intersect(right).size
        if (overlap == 0) return false
        val containment = overlap.toDouble() / minOf(left.size, right.size).toDouble()
        return containment >= 0.60 && (overlap >= 2 || minOf(left.size, right.size) == 1)
    }

    private fun topicTokens(value: String): Set<String> {
        return normalize(value)
            .split(' ')
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filterNot { token -> token.all(Char::isDigit) }
            .filterNot { it in TOPIC_STOP_WORDS }
            .filterNot { it in CURRENCY_TOKENS }
            .toCollection(linkedSetOf())
    }

    private fun humanizeTopic(tokens: Set<String>): String {
        val chosen = tokens.take(4)
        if (chosen.isEmpty()) return "Developing situation"
        return chosen.joinToString(" ") { token ->
            if (token.any { it.code > 127 }) token
            else token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun extractEntity(text: String): String? {
        val segments = text.split(':')
        if (segments.size < 2) return null
        return segments
            .dropLast(1)
            .takeLast(3)
            .asReversed()
            .map { it.trim() }
            .firstOrNull { candidate ->
                val words = candidate.split(Regex("\\s+")).filter(String::isNotBlank)
                candidate.length in 2..48 &&
                    words.size in 1..4 &&
                    candidate.any(Char::isLetter) &&
                    ENTITY_REJECT_WORDS.none { normalize(candidate).contains(it) }
            }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun canonicalCurrency(value: String): String {
        val normalized = value.lowercase(Locale.ROOT).replace(".", "").trim()
        return when {
            normalized in setOf("egp", "le", "جنيه", "جنية") -> "EGP"
            normalized in setOf("usd", "$") -> "USD"
            normalized in setOf("eur", "€") -> "EUR"
            else -> value.uppercase(Locale.ROOT)
        }
    }

    private fun sameAmount(left: Double, right: Double): Boolean = abs(left - right) < 0.005

    private fun percentChange(previous: Double, latest: Double): Double =
        if (previous == 0.0) 0.0 else ((latest - previous) / previous) * 100.0

    private fun formatAmount(value: Double): String = if (abs(value - value.toLong()) < 0.005) {
        String.format(Locale.US, "%,d", value.toLong())
    } else {
        String.format(Locale.US, "%,.2f", value)
    }

    private fun formatPercent(value: Double): String = String.format(
        Locale.US,
        "%+.1f%%",
        value
    )

    companion object {
        private const val COMPLETION_INSIGHT_WINDOW_MILLIS = 24 * 60 * 60 * 1000L
        private const val MAX_INSIGHTS = 6
        private const val MAX_SITUATIONS = 8

        private val AMOUNT_THEN_CURRENCY = Regex(
            """(?i)(\d[\d,]*(?:\.\d+)?)\s*(egp|e\.?g\.?p\.?|l\.?e\.?|جنيه|جنية|usd|\$|eur|€)"""
        )
        private val CURRENCY_THEN_AMOUNT = Regex(
            """(?i)(egp|e\.?g\.?p\.?|l\.?e\.?|جنيه|جنية|usd|\$|eur|€)\s*(\d[\d,]*(?:\.\d+)?)"""
        )

        private val CURRENCY_TOKENS = setOf(
            "egp", "e", "g", "p", "le", "usd", "eur", "جنيه", "جنية"
        )

        private val TOPIC_STOP_WORDS = setOf(
            "the", "and", "that", "this", "with", "from", "into", "for", "you", "your", "our", "their",
            "new", "latest", "updated", "update", "revised", "revision", "revise", "quotation", "quote", "pricing",
            "price", "amount", "total", "value", "cost", "attached", "attachment", "send", "sent", "sending", "share",
            "shared", "will", "would", "tomorrow", "today", "received", "receive", "please", "review", "approval",
            "approved", "pending", "final", "version", "عرض", "سعر", "السعر", "اسعار", "الاسعار", "مبلغ", "اجمالي",
            "الاجمالي", "قيمة", "القيمة", "معدل", "المعدل", "تعديل", "جديد", "الجديد", "مرفق", "ارسال", "ارسل",
            "بعت", "هبعت", "هابعت", "بكرة", "بكره", "اليوم", "النهاردة", "النهارده", "مراجعة", "اعتماد", "معتمد"
        )

        private val ENTITY_REJECT_WORDS = setOf(
            "http", "https", "today", "tomorrow", "اليوم", "بكره", "بكرة"
        )
    }
}
