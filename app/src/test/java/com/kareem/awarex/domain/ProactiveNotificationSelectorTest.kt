package com.kareem.awarex.domain

import com.kareem.awarex.core.model.AttentionCard
import com.kareem.awarex.core.model.InsightCard
import com.kareem.awarex.core.model.InsightKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProactiveNotificationSelectorTest {
    @Test
    fun overdueCommitmentBeatsHighValueInsight() {
        val overdue = AttentionCard(
            loopId = 7,
            title = "Still waiting",
            reason = "Expected time passed",
            evidenceText = "Ahmed will send tomorrow",
            dueAt = 1_000,
            overdue = true
        )
        val insight = InsightCard(
            id = "price-change",
            kind = InsightKind.CHANGE,
            title = "Quotation changed",
            body = "420,000 → 447,000",
            whyItMatters = "Material change",
            evidenceTexts = emptyList(),
            confidence = 0.94,
            priority = 98,
            createdAt = 2_000
        )

        val candidate = ProactiveNotificationSelector.select(listOf(overdue), listOf(insight))

        assertEquals("overdue:7", candidate?.key)
    }

    @Test
    fun lowPriorityInsightDoesNotInterrupt() {
        val insight = InsightCard(
            id = "small-change",
            kind = InsightKind.CHANGE,
            title = "Small change",
            body = "100 → 101",
            whyItMatters = "Minor",
            evidenceTexts = emptyList(),
            confidence = 0.9,
            priority = 80,
            createdAt = 2_000
        )

        assertNull(ProactiveNotificationSelector.select(emptyList(), listOf(insight)))
    }

    @Test
    fun monitoringWithoutOverdueDoesNotInterrupt() {
        val monitoring = AttentionCard(
            loopId = 9,
            title = "Watching this",
            reason = "Still open",
            evidenceText = "Will send tomorrow",
            dueAt = 9_999,
            overdue = false
        )

        assertNull(ProactiveNotificationSelector.select(listOf(monitoring), emptyList()))
    }
}
