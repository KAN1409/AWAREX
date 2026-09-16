package com.kareem.awarex.domain

import com.kareem.awarex.core.model.InsightKind
import com.kareem.awarex.core.model.Observation
import com.kareem.awarex.core.model.OpenLoop
import com.kareem.awarex.core.model.SituationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldModelEngineTest {
    private val engine = WorldModelEngine()

    @Test
    fun linksTwoPriceFactsAndDiscoversMaterialChange() {
        val snapshot = engine.build(
            observations = listOf(
                observation(1, "Negma marble quotation 420000 EGP", 1_000),
                observation(2, "Revised Negma marble quotation 447000 EGP", 2_000)
            ),
            loops = emptyList(),
            now = 3_000
        )

        assertEquals(1, snapshot.insights.size)
        val insight = snapshot.insights.single()
        assertEquals(InsightKind.CHANGE, insight.kind)
        assertTrue(insight.title.contains("Negma", ignoreCase = true))
        assertTrue(insight.body.contains("420,000"))
        assertTrue(insight.body.contains("447,000"))
        assertTrue(insight.body.contains("+6.4%"))
        assertEquals(SituationState.CHANGED, snapshot.situations.single().state)
    }

    @Test
    fun doesNotCompareUnrelatedQuotations() {
        val snapshot = engine.build(
            observations = listOf(
                observation(1, "Negma marble quotation 420000 EGP", 1_000),
                observation(2, "Palmariva wood quotation 447000 EGP", 2_000)
            ),
            loops = emptyList(),
            now = 3_000
        )

        assertTrue(snapshot.insights.isEmpty())
        assertEquals(2, snapshot.situations.size)
    }

    @Test
    fun sameValueDoesNotCreateFalseChange() {
        val snapshot = engine.build(
            observations = listOf(
                observation(1, "Negma marble quotation 420000 EGP", 1_000),
                observation(2, "Updated Negma marble quotation 420000 EGP", 2_000)
            ),
            loops = emptyList(),
            now = 3_000
        )

        assertTrue(snapshot.insights.isEmpty())
        assertEquals(SituationState.DEVELOPING, snapshot.situations.single().state)
    }

    @Test
    fun supportsArabicPriceEvidence() {
        val snapshot = engine.build(
            observations = listOf(
                observation(1, "عرض سعر نجمة رخام 420000 جنيه", 1_000),
                observation(2, "عرض السعر المعدل نجمة رخام 447000 جنيه", 2_000)
            ),
            loops = emptyList(),
            now = 3_000
        )

        assertEquals(1, snapshot.insights.size)
        assertTrue(snapshot.insights.single().body.contains("+6.4%"))
        assertEquals(SituationState.CHANGED, snapshot.situations.single().state)
    }

    @Test
    fun resolvedCommitmentBecomesResolvedSituationAndCompletionInsight() {
        val created = observation(1, "Ahmed: I'll send the revised quotation tomorrow", 1_000)
        val resolution = observation(2, "Ahmed: Sent the revised quotation", 2_000)
        val loop = OpenLoop(
            id = 9,
            title = created.text,
            normalizedSubject = "ahmed revised quotation",
            createdFromObservationId = created.id,
            dueAt = null,
            createdAt = created.observedAt,
            resolvedAt = resolution.observedAt,
            resolutionObservationId = resolution.id
        )

        val snapshot = engine.build(
            observations = listOf(created, resolution),
            loops = listOf(loop),
            now = 2_500
        )

        assertEquals(SituationState.RESOLVED, snapshot.situations.single().state)
        assertEquals(InsightKind.COMPLETION, snapshot.insights.single().kind)
        assertEquals(2, snapshot.insights.single().evidenceTexts.size)
    }

    @Test
    fun priceSituationAbsorbsRelatedCommitmentInsteadOfDuplicatingIt() {
        val created = observation(1, "Ahmed: I'll send the Negma marble quotation tomorrow", 1_000)
        val price = observation(2, "Negma marble quotation 420000 EGP", 2_000)
        val loop = OpenLoop(
            id = 3,
            title = created.text,
            normalizedSubject = "ahmed negma marble quotation",
            createdFromObservationId = created.id,
            dueAt = 50_000,
            createdAt = created.observedAt,
            resolvedAt = null,
            resolutionObservationId = null
        )

        val snapshot = engine.build(
            observations = listOf(created, price),
            loops = listOf(loop),
            now = 3_000
        )

        assertEquals(1, snapshot.situations.size)
        assertEquals(SituationState.WAITING, snapshot.situations.single().state)
        assertTrue(snapshot.situations.single().summary.contains("commitment is still open", ignoreCase = true))
    }

    private fun observation(id: Long, text: String, at: Long) = Observation(
        id = id,
        text = text,
        source = "manual",
        observedAt = at
    )
}
