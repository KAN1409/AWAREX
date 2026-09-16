package com.kareem.awarex.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceEventMatcherTest {
    private val matcher = EvidenceEventMatcher()

    @Test
    fun senderPrefixedNotificationAndManualCommitmentAreSameEvent() {
        val base = 1_800_000_000_000L
        assertTrue(
            matcher.sameEvent(
                "Kareem Abdel Nasser: Ahmed: I'll send the revised quotation tomorrow",
                base + 120_000,
                "Ahmed: I'll send the revised quotation tomorrow",
                base
            )
        )
    }

    @Test
    fun unrelatedCommitmentsRemainSeparateEvidence() {
        val base = 1_800_000_000_000L
        assertFalse(
            matcher.sameEvent(
                "Ahmed: I'll send the revised quotation tomorrow",
                base,
                "Mona: I'll prepare the drawings tomorrow",
                base + 30_000
            )
        )
    }

    @Test
    fun sameTextFarApartIsNotCollapsed() {
        val base = 1_800_000_000_000L
        assertFalse(
            matcher.sameEvent(
                "Ahmed: I'll send the revised quotation tomorrow",
                base,
                "Ahmed: I'll send the revised quotation tomorrow",
                base + EvidenceEventMatcher.EVENT_WINDOW_MILLIS + 1
            )
        )
    }

    @Test
    fun exactCrossSourceObservationWithinWindowIsSameEvent() {
        val base = 1_800_000_000_000L
        assertTrue(
            matcher.sameEvent(
                "The revised quotation is attached",
                base,
                "The revised quotation is attached",
                base + 15_000
            )
        )
    }
}
