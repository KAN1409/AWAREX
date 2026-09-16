package com.kareem.awarex.data

import com.kareem.awarex.core.model.AttentionCard
import com.kareem.awarex.core.model.Observation
import com.kareem.awarex.core.model.OpenLoop
import com.kareem.awarex.domain.CommitmentEngine
import kotlin.math.abs

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
        collapseRecentDuplicateOpenLoops()
        val observation = store.insertObservation(text, source, timestamp)

        val activeBefore = store.activeOpenLoops()
        val resolution = commitmentEngine.matchingResolution(observation.text, activeBefore)
        val resolvedId = resolution
            ?.takeIf { store.resolveOpenLoop(it.id, observation.id, timestamp) }
            ?.id

        val candidate = commitmentEngine.extract(observation.text, timestamp)
        val duplicateActiveLoop = candidate?.let { incoming ->
            activeBefore.any { existing ->
                !existing.isResolved &&
                    abs(timestamp - existing.createdAt) <= DUPLICATE_LOOP_WINDOW_MILLIS &&
                    commitmentEngine.sameSubject(incoming.normalizedSubject, existing.normalizedSubject)
            }
        } == true

        val created = if (candidate != null && !duplicateActiveLoop) {
            store.insertOpenLoop(
                title = candidate.title,
                normalizedSubject = candidate.normalizedSubject,
                observationId = observation.id,
                dueAt = candidate.dueAt,
                createdAt = timestamp
            )
        } else null

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

        val candidate = commitmentEngine.extract(clean, timestamp)
        if (candidate != null) {
            val recentEquivalentLoop = store.activeOpenLoops().any { loop ->
                loop.createdAt >= since &&
                    commitmentEngine.sameSubject(candidate.normalizedSubject, loop.normalizedSubject)
            }
            if (recentEquivalentLoop) return null
        }

        return capture(clean, source)
    }

    fun attentionCards(): List<AttentionCard> {
        collapseRecentDuplicateOpenLoops()
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

    private fun collapseRecentDuplicateOpenLoops() {
        val active = store.activeOpenLoops().sortedBy { it.createdAt }
        val kept = mutableListOf<OpenLoop>()
        active.forEach { loop ->
            val duplicate = kept.any { existing ->
                abs(loop.createdAt - existing.createdAt) <= DUPLICATE_LOOP_WINDOW_MILLIS &&
                    commitmentEngine.sameSubject(loop.normalizedSubject, existing.normalizedSubject)
            }
            if (duplicate) {
                store.deleteOpenLoop(loop.id)
            } else {
                kept += loop
            }
        }
    }

    companion object {
        private const val DUPLICATE_LOOP_WINDOW_MILLIS = 120_000L
    }
}
