package com.brokenpip3.fatto.data.model

import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.DateDisplayModes

fun calendarDateModes(): DateDisplayModes =
    DateDisplayModes(DateDisplayMode.CALENDAR_DATE, DateDisplayMode.CALENDAR_DATE, DateDisplayMode.CALENDAR_DATE)

fun DateDisplayModes.forField(field: String): DateDisplayMode =
    when (field) {
        "due" -> due
        "scheduled" -> scheduled
        "wait" -> wait
        else -> error("Unknown task date: $field")
    }

fun DateDisplayModes.withField(
    field: String,
    mode: DateDisplayMode,
): DateDisplayModes =
    when (field) {
        "due" -> copy(due = mode)
        "scheduled" -> copy(scheduled = mode)
        "wait" -> copy(wait = mode)
        else -> error("Unknown task date: $field")
    }

/** Creation derives Wait only when it was absent and a source timestamp was selected. */
fun uniffi.taskchampion_android.TaskAddProps.withResolvedWait(value: String?): uniffi.taskchampion_android.TaskAddProps {
    val source =
        listOf("due" to due, "scheduled" to scheduled)
            .mapNotNull { (key, raw) -> com.brokenpip3.fatto.data.DateTimeUtils.parseToInstant(raw)?.let { key to it } }
            .minByOrNull { it.second }
    val modes = if (value != wait && source != null) dateModes.withField("wait", dateModes.forField(source.first)) else dateModes
    return copy(wait = value, dateModes = modes)
}

fun Task.dateValue(field: String): com.brokenpip3.fatto.data.TaskDateValue {
    val timestamp =
        when (field) {
            "due" -> due
            "scheduled" -> scheduled
            "wait" -> wait
            else -> error("Unknown task date: $field")
        }
    return com.brokenpip3.fatto.data.TaskDateValue(timestamp, dateModes.forField(field))
}

fun dateDisplayKey(field: String): String {
    require(field in setOf("due", "scheduled", "wait")) { "Unknown task date: $field" }
    return "fatto_display_$field"
}

fun Task.dateDisplayBaseline(field: String): String? = udas[dateDisplayKey(field)]

val Task.visibleUdas: Map<String, String>
    get() =
        udas.filterNot { (key, value) ->
            value == "v1:local-datetime" &&
                listOf("due", "scheduled", "wait").any { key == dateDisplayKey(it) }
        }
