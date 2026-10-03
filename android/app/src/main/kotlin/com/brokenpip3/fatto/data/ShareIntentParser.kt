package com.brokenpip3.fatto.data

import java.time.LocalDate
import java.time.ZoneOffset

/** What an `ACTION_SEND` share carried, beyond the plain text every sender provides. */
data class SharedTask(
    val description: String,
    val project: String? = null,
    val tags: List<String> = emptyList(),
    val due: String? = null,
)

object ShareIntentParser {
    /**
     * App-scoped extras a senders may add to prefill the New Task dialog — a voice
     * dictation tool, a taskwarrior-shaped client, anything that knows the names.
     * Senders that don't send them are unaffected: an absent extra reads as null
     * and leaves the field to the dialog's own seeding (active filter, default
     * project).
     */
    const val EXTRA_PROJECT = "com.brokenpip3.fatto.extra.PROJECT"

    const val EXTRA_TAGS = "com.brokenpip3.fatto.extra.TAGS"

    const val EXTRA_DUE = "com.brokenpip3.fatto.extra.DUE"

    fun descriptionFrom(extraText: String?): String? = extraText?.trim()?.takeIf { it.isNotBlank() }

    fun projectFrom(extraProject: String?): String? = extraProject?.trim()?.takeIf { it.isNotBlank() }

    /** A comma-separated list: `dom, sklep` → `["dom", "sklep"]`, blanks dropped. */
    fun tagsFrom(extraTags: String?): List<String> =
        extraTags
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    /**
     * The due date as the New Task dialog stores it: an ISO-8601 timestamp.
     * A bare date (`2026-10-05`) is read as midnight UTC, which keeps the same
     * day under the floating-date display ([DateTimeUtils.parseToLocalDate] only
     * reads the first ten characters). Anything unparseable is dropped: an
     * unusable extra opens the dialog without it rather than failing the share.
     */
    fun dueFrom(extraDue: String?): String? {
        val raw = extraDue?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val instant =
            DateTimeUtils.parseToInstant(raw)
                ?: runCatching {
                    LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant()
                }.getOrNull()
        return instant?.toString()
    }
}
