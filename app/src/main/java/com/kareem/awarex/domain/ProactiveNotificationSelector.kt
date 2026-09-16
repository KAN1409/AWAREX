package com.kareem.awarex.domain

import com.kareem.awarex.core.model.AttentionCard
import com.kareem.awarex.core.model.InsightCard

object ProactiveNotificationSelector {
    data class Candidate(
        val key: String,
        val title: String,
        val body: String,
        val priority: Int
    )

    fun select(attention: List<AttentionCard>, insights: List<InsightCard>): Candidate? {
        val overdue = attention
            .asSequence()
            .filter { it.overdue }
            .map {
                Candidate(
                    key = "overdue:${it.loopId}",
                    title = it.title,
                    body = it.evidenceText,
                    priority = 100
                )
            }
            .maxByOrNull { it.priority }

        if (overdue != null) return overdue

        return insights
            .asSequence()
            .filter { it.priority >= HIGH_VALUE_INSIGHT_THRESHOLD }
            .map {
                Candidate(
                    key = "insight:${it.id}",
                    title = it.title,
                    body = it.body,
                    priority = it.priority
                )
            }
            .maxByOrNull { it.priority }
    }

    private const val HIGH_VALUE_INSIGHT_THRESHOLD = 90
}
