package com.kareem.awarex.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationEvidenceExtractorTest {
    @Test
    fun titleAndBodyBecomeGroundedEvidence() {
        assertEquals(
            "Ahmed: I'll send the revised quotation tomorrow",
            NotificationEvidenceExtractor.extract(
                NotificationEvidenceExtractor.Payload(
                    title = "Ahmed",
                    text = "I'll send the revised quotation tomorrow"
                )
            )
        )
    }

    @Test
    fun bigTextWinsOverTruncatedNormalText() {
        assertEquals(
            "Project group: Full detailed message",
            NotificationEvidenceExtractor.extract(
                NotificationEvidenceExtractor.Payload(
                    title = "Project group",
                    bigText = "Full detailed message",
                    text = "Full detailed..."
                )
            )
        )
    }

    @Test
    fun duplicatedTitleIsNotRepeated() {
        assertEquals(
            "Ahmed: Sent the quotation",
            NotificationEvidenceExtractor.extract(
                NotificationEvidenceExtractor.Payload(
                    title = "Ahmed",
                    text = "Ahmed: Sent the quotation"
                )
            )
        )
    }

    @Test
    fun textLinesAreUsedWhenSingleBodyIsMissing() {
        assertEquals(
            "Team: first · second",
            NotificationEvidenceExtractor.extract(
                NotificationEvidenceExtractor.Payload(
                    title = "Team",
                    lines = listOf(" first ", "", "second")
                )
            )
        )
    }

    @Test
    fun emptyNotificationProducesNoEvidence() {
        assertNull(NotificationEvidenceExtractor.extract(NotificationEvidenceExtractor.Payload()))
    }
}
