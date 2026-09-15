package com.kareem.awarex.data

import com.kareem.awarex.core.model.AttentionCard
import com.kareem.awarex.core.model.Observation
import com.kareem.awarex.domain.CommitmentEngine

class AwareRepository(
    private val store: AwareStore,
    private val commitmentEngine: CommitmentEngine = CommitmentEngine(),
    private val now: () -> Long = System::currentTimeMillis
) {
    data class CaptureResult(
        val observation: Observation,
        val createdLoopId: Long?,
        val resolvedLoopId: Long?
    )

    fun capture(text: String, source: String = "manual"): CaptureResult {
        val timestamp = now()
        val observation = store.insertObservation(text, source, timestamp)

        val activeBefore = store.activeOpenLoops()
        val resolution = commitmentEngine.matchingResolution(observation.text, activeBefore)
        val resolvedId = resolution
            ?.takeIf { store.resolveOpenLoop(it.id, observation.id, timestamp) }
            ?.id

        val candidate = commitmentEngine.extract(observation.text, timestamp)
        val created = candidate?.let {
            store.insertOpenLoop(
                title = it.title,
                normalizedSubject = it.normalizedSubject,
                observationId = observation.id,
                dueAt = it.dueAt,
                createdAt = timestamp
            )
        }

        return CaptureResult(
            observation = observation,
            createdLoopId = created?.id,
            resolvedLoopId = resolvedId
        )
    }

    fun captureIfNew(
        text: String,
        source: String,
        dedupeWindowMillis: Long = 15_000L
    ): CaptureResult? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val timestamp = now()
        val since = (timestamp - dedupeWindowMillis.coerceAtLeast(0L)).coerceAtLeast(0L)
        if (store.hasRecentObservation(clean, source, since)) return null
        return capture(clean, source)
    }

    fun attentionCards(): List<AttentionCard> {
        val timestamp = now()
        return store.activeOpenLoops().mapNotNull { loop ->
            val evidence = store.observation(loop.createdFromObservationId) ?: return@mapNotNull null
            val overdue = loop.dueAt?.let { it < timestamp } == true
            AttentionCard(
                loopId = loop.id,
                title = if (overdue) "Still waiting" else "Watching this",
                reason = if (overdue) {
                    "The expected time passed and AWAREX has not seen matching completion evidence."
                } else {
                    "A commitment was detected and remains open."
                },
                evidenceText = evidence.text,
                dueAt = loop.dueAt,
                overdue = overdue
            )
        }
    }

    fun recentEvidence(limit: Int = 20): List<Observation> = store.recentObservations(limit)

    fun close() = store.close()
}
