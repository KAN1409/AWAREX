package com.kareem.awarex.domain

import kotlin.math.abs

/**
 * Identifies multiple observations that describe the same real-world evidence event.
 * Raw observations can remain in storage, while the user-facing evidence timeline stays canonical.
 */
class EvidenceEventMatcher(
    private val commitmentEngine: CommitmentEngine = CommitmentEngine()
) {
    fun sameEvent(
        leftText: String,
        leftObservedAt: Long,
        rightText: String,
        rightObservedAt: Long
    ): Boolean {
        if (abs(leftObservedAt - rightObservedAt) > EVENT_WINDOW_MILLIS) return false

        val left = leftText.trim().replace(Regex("\\s+"), " ")
        val right = rightText.trim().replace(Regex("\\s+"), " ")
        if (left.equals(right, ignoreCase = true)) return true

        val leftCommitment = commitmentEngine.extract(left, leftObservedAt) ?: return false
        val rightCommitment = commitmentEngine.extract(right, rightObservedAt) ?: return false
        return commitmentEngine.sameSubject(
            leftCommitment.normalizedSubject,
            rightCommitment.normalizedSubject
        )
    }

    companion object {
        const val EVENT_WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
