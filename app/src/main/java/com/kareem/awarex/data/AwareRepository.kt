package com.kareem.awarex.data

import com.kareem.awarex.core.model.AttentionCard
import com.kareem.awarex.core.model.Observation
import com.kareem.awarex.core.model.OpenLoop
import com.kareem.awarex.core.model.WorldSnapshot
import com.kareem.awarex.domain.CommitmentEngine
import com.kareem.awarex.domain.EvidenceEventMatcher
import com.kareem.awarex.domain.EvidenceRelevancePolicy
import com.kareem.awarex.domain.WorldModelEngine
import kotlin.math.abs

class AwareRepository(
    private val store: AwareStore,
    private val commitmentEngine: CommitmentEngine = CommitmentEngine(),
    private val evidenceEventMatcher: EvidenceEventMatcher = EvidenceEventMatcher(commitmentEngine),
    private val worldModelEngine: WorldModelEngine = WorldModelEngine(),
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
        if (!EvidenceRelevancePolicy.shouldPersist(clean, source)) return null

        val timestamp = now()
        val since = (timestamp - dedupeWindowMillis.coerceAtLeast(0L)).coerceAtLeast(0L)
        if (store.hasRecentObservation(clean, source, since)) return null

        val semanticSince = (timestamp - EvidenceEventMatcher.EVENT_WINDOW_MILLIS).coerceAtLeast(0L)
        val sameRecentEvent = store.recentObservations(30).any { previous ->
            previous.observedAt >= semanticSince &&
                EvidenceRelevancePolicy.shouldSurface(previous.text, previous.source) &&
                evidenceEventMatcher.sameEvent(
                    clean,
                    timestamp,
                    previous.text,
                    previous.observedAt
                )
        }
        if (sameRecentEvent) return null

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

    fun worldSnapshot(): WorldSnapshot {
        collapseRecentDuplicateOpenLoops()
        val loops = store.recentOpenLoops(WORLD_LOOP_LIMIT)
        val canonical = canonicalEvidence(WORLD_EVIDENCE_LIMIT).toMutableList()
        val knownIds = canonical.mapTo(mutableSetOf()) { it.id }
        loops.asSequence()
            .flatMap { loop -> sequenceOf(loop.createdFromObservationId, loop.resolutionObservationId) }
            .filterNotNull()
            .distinct()
            .filterNot(knownIds::contains)
            .mapNotNull(store::observation)
            .filter { EvidenceRelevancePolicy.shouldSurface(it.text, it.source) }
            .forEach {
                canonical += it
                knownIds += it.id
            }

        return worldModelEngine.build(
            observations = canonical,
            loops = loops,
            now = now()
        )
    }

    fun recentEvidence(limit: Int = 20): List<Observation> =
        canonicalEvidence(limit.coerceIn(1, 50))

    fun close() = store.close()

    private fun canonicalEvidence(limit: Int): List<Observation> {
        val safeLimit = limit.coerceIn(1, WORLD_EVIDENCE_LIMIT)
        val canonical = mutableListOf<Observation>()
        store.recentObservations((safeLimit * 8).coerceAtMost(WORLD_EVIDENCE_LIMIT))
            .asSequence()
            .filter { EvidenceRelevancePolicy.shouldSurface(it.text, it.source) }
            .forEach { observation ->
                val duplicate = canonical.any { existing ->
                    evidenceEventMatcher.sameEvent(
                        observation.text,
                        observation.observedAt,
                        existing.text,
                        existing.observedAt
                    )
                }
                if (!duplicate) canonical += observation
                if (canonical.size >= safeLimit) return canonical
            }
        return canonical
    }

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
        private const val WORLD_EVIDENCE_LIMIT = 200
        private const val WORLD_LOOP_LIMIT = 100
    }
}
