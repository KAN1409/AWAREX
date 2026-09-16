package com.kareem.awarex.core.model

enum class SituationState {
    WAITING,
    CHANGED,
    RESOLVED,
    DEVELOPING
}

enum class InsightKind {
    CHANGE,
    COMPLETION,
    DISCOVERY
}

data class InsightCard(
    val id: String,
    val kind: InsightKind,
    val title: String,
    val body: String,
    val whyItMatters: String,
    val evidenceTexts: List<String>,
    val confidence: Double,
    val priority: Int,
    val createdAt: Long
)

data class SituationCard(
    val id: String,
    val title: String,
    val state: SituationState,
    val summary: String,
    val evidenceCount: Int,
    val updatedAt: Long,
    val primaryEntity: String? = null
)

data class WorldSnapshot(
    val insights: List<InsightCard> = emptyList(),
    val situations: List<SituationCard> = emptyList()
)
