package com.kareem.awarex.domain

import com.kareem.awarex.core.model.OpenLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CommitmentEngineTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private val engine = CommitmentEngine(zone)
    private val now = Instant.parse("2026-09-16T08:00:00Z").toEpochMilli()

    @Test
    fun englishCommitmentCreatesTrackableSubjectAndDueTime() {
        val candidate = engine.extract("I'll send the revised quotation tomorrow", now)
        assertNotNull(candidate)
        assertEquals("revised quotation", candidate!!.normalizedSubject)
        assertTrue(candidate.dueAt!! > now)
    }

    @Test
    fun egyptianArabicCommitmentIsDetected() {
        val candidate = engine.extract("هبعت عرض السعر المعدل بكرة", now)
        assertNotNull(candidate)
        assertEquals("عرض السعر المعدل", candidate!!.normalizedSubject)
        assertTrue(candidate.dueAt!! > now)
    }

    @Test
    fun ordinaryStatementDoesNotCreateOpenLoop() {
        assertNull(engine.extract("The quotation is in the project folder", now))
    }

    @Test
    fun completionEvidenceClosesTheBestMatchingLoop() {
        val loop = OpenLoop(
            id = 7,
            title = "I'll send the revised quotation tomorrow",
            normalizedSubject = "revised quotation",
            createdFromObservationId = 1,
            dueAt = now + 86_400_000,
            createdAt = now,
            resolvedAt = null,
            resolutionObservationId = null
        )
        val match = engine.matchingResolution("Sent the revised quotation", listOf(loop))
        assertEquals(7L, match?.id)
    }

    @Test
    fun unrelatedCompletionDoesNotCloseLoop() {
        val loop = OpenLoop(
            id = 9,
            title = "I'll send the revised quotation tomorrow",
            normalizedSubject = "revised quotation",
            createdFromObservationId = 1,
            dueAt = null,
            createdAt = now,
            resolvedAt = null,
            resolutionObservationId = null
        )
        assertNull(engine.matchingResolution("Done with the bathroom drawings", listOf(loop)))
    }
}
