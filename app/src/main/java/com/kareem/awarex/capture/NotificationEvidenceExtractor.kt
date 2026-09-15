package com.kareem.awarex.capture

object NotificationEvidenceExtractor {
    data class Payload(
        val title: String = "",
        val bigText: String = "",
        val text: String = "",
        val lines: List<String> = emptyList()
    )

    fun extract(payload: Payload): String? {
        val title = payload.title.clean()
        val bigText = payload.bigText.clean()
        val normalText = payload.text.clean()
        val lines = payload.lines.map(String::clean).filter(String::isNotEmpty).joinToString(" · ")

        val body = sequenceOf(bigText, normalText, lines).firstOrNull { it.isNotEmpty() }.orEmpty()
        if (body.isEmpty() && title.isEmpty()) return null

        return when {
            title.isNotEmpty() && body.isNotEmpty() && !body.startsWith(title, ignoreCase = true) -> "$title: $body"
            body.isNotEmpty() -> body
            else -> title
        }.trim().ifEmpty { null }
    }

    private fun String.clean(): String = trim().replace(Regex("\\s+"), " ")
}
