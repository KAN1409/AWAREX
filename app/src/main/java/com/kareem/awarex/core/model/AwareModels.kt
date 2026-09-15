package com.kareem.awarex.core.model

data class Observation(
    val id: Long,
    val text: String,
    val source: String,
    val observedAt: Long
)

data class OpenLoop(
    val id: Long,
    val title: String,
    val normalizedSubject: String,
    val createdFromObservationId: Long,
    val dueAt: Long?,
    val createdAt: Long,
    val resolvedAt: Long?,
    val resolutionObservationId: Long?
) {
    val isResolved: Boolean get() = resolvedAt != null
}

data class AttentionCard(
    val loopId: Long,
    val title: String,
    val reason: String,
    val evidenceText: String,
    val dueAt: Long?,
    val overdue: Boolean
)
