package com.brokenpip3.fatto.data

data class ParsedQuery(
    val project: String? = null,
    val tags: Set<String> = emptySet(),
    val uuid: String? = null,
    val description: String = "",
)

object SearchParser {
    private val projectRegex = Regex("(?i)project:\\s*([^\\s]+)")
    private val tagsRegex = Regex("(?i)tags:\\s*([^\\s]+)")
    private val uuidRegex = Regex("(?i)uuid:\\s*([^\\s]+)")

    fun parse(query: String): ParsedQuery {
        val projectMatch = projectRegex.find(query)
        val tagsMatch = tagsRegex.find(query)
        val uuidMatch = uuidRegex.find(query)

        val project = projectMatch?.groupValues?.get(1)
        val tags = tagsMatch?.groupValues?.get(1)?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        val uuid = uuidMatch?.groupValues?.get(1)

        var description = query
        projectMatch?.let { description = description.replace(it.value, "") }
        tagsMatch?.let { description = description.replace(it.value, "") }
        uuidMatch?.let { description = description.replace(it.value, "") }

        return ParsedQuery(
            project = project,
            tags = tags,
            uuid = uuid,
            description = description.trim().replace(Regex("\\s+"), " "),
        )
    }
}
