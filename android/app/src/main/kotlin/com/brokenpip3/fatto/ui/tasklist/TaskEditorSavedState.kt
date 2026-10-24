package com.brokenpip3.fatto.ui.tasklist

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import com.brokenpip3.fatto.data.model.Annotation
import com.brokenpip3.fatto.data.model.RecurrenceRule
import com.brokenpip3.fatto.data.model.Task
import org.json.JSONArray
import org.json.JSONObject
import uniffi.taskchampion_android.DateDisplayMode
import uniffi.taskchampion_android.DateDisplayModes
import uniffi.taskchampion_android.RecurrenceData
import uniffi.taskchampion_android.TaskStatus

/** Saved drafts contain the original conflict snapshot; restoring one never calls a mutation. */
internal object TaskEditorSavedState {
    private fun JSONObject.nullable(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.strings(): Map<String, String> = keys().asSequence().associateWith { getString(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun JSONObject.putNullable(
        key: String,
        value: String?,
    ): JSONObject = put(key, value ?: JSONObject.NULL)

    private fun modes(value: DateDisplayModes): JSONObject =
        JSONObject()
            .put("due", value.due.name).put("scheduled", value.scheduled.name).put("wait", value.wait.name)

    private fun modes(value: JSONObject): DateDisplayModes =
        DateDisplayModes(
            DateDisplayMode.valueOf(value.getString("due")), DateDisplayMode.valueOf(value.getString("scheduled")),
            DateDisplayMode.valueOf(value.getString("wait")),
        )

    private fun annotations(value: List<Annotation>): JSONArray =
        JSONArray(
            value.map {
                JSONObject().put("entry", it.entry).put("description", it.description)
            },
        )

    private fun annotations(value: JSONArray): List<Annotation> =
        (0 until value.length()).map {
            val item = value.getJSONObject(it)
            Annotation(item.getString("entry"), item.getString("description"))
        }

    private fun recurrence(value: RecurrenceData): JSONObject =
        JSONObject().apply {
            listOf(
                "recur" to value.recur, "rtype" to value.rtype, "parent" to value.parent, "imask" to value.imask,
                "mask" to value.mask, "untilRaw" to value.untilRaw, "until" to value.until, "last" to value.last,
                "template" to value.template,
            ).forEach { (key, raw) -> putNullable(key, raw) }
        }

    private fun recurrence(value: JSONObject): RecurrenceData =
        RecurrenceData(
            value.nullable("recur"), value.nullable("rtype"), value.nullable("parent"), value.nullable("imask"),
            value.nullable("mask"), value.nullable("untilRaw"), value.nullable("until"), value.nullable("last"), value.nullable("template"),
        )

    private fun status(value: TaskStatus): JSONObject =
        JSONObject().apply {
            put(
                "kind",
                when (value) {
                    TaskStatus.Pending -> "pending"
                    TaskStatus.Completed -> "completed"
                    TaskStatus.Deleted -> "deleted"
                    TaskStatus.Recurring -> "recurring"
                    is TaskStatus.Unknown -> "unknown"
                },
            )
            if (value is TaskStatus.Unknown) put("raw", value.value)
        }

    private fun status(value: JSONObject): TaskStatus =
        when (value.getString("kind")) {
            "pending" -> TaskStatus.Pending
            "completed" -> TaskStatus.Completed
            "deleted" -> TaskStatus.Deleted
            "recurring" -> TaskStatus.Recurring
            "unknown" -> TaskStatus.Unknown(value.getString("raw"))
            else -> error("Unknown saved task status")
        }

    fun encodeTask(task: Task): String =
        JSONObject().apply {
            put("v", 1)
            put("uuid", task.uuid)
            put("description", task.description)
            put("status", status(task.status))
            put("tags", JSONArray(task.tags))
            putNullable("due", task.due)
            putNullable("entry", task.entry)
            putNullable("project", task.project)
            putNullable("wait", task.wait)
            putNullable("scheduled", task.scheduled)
            putNullable("start", task.start)
            putNullable("priority", task.priority)
            put("urgency", task.urgency.toString())
            put("blocked", task.isBlocked)
            put("blocking", task.isBlocking)
            put("dependencies", JSONArray(task.dependencies))
            put("udas", JSONObject(task.udas))
            put("annotations", annotations(task.annotations))
            put("recurrence", task.recurrence?.let(::recurrence) ?: JSONObject.NULL)
            put("nativeDates", JSONObject(task.nativeDates))
            putNullable("modified", task.modified)
            put("modes", modes(task.dateModes))
        }.toString()

    fun decodeTask(encoded: String): Task {
        val value = JSONObject(encoded)
        require(value.getInt("v") == 1)
        return Task(
            uuid = value.getString("uuid"), description = value.getString("description"), status = status(value.getJSONObject("status")),
            tags = value.getJSONArray("tags").strings(), due = value.nullable("due"), entry = value.nullable("entry"),
            project = value.nullable("project"), wait = value.nullable("wait"), scheduled = value.nullable("scheduled"),
            start = value.nullable("start"), priority = value.nullable("priority"), urgency = value.getString("urgency").toFloat(),
            isBlocked = value.getBoolean("blocked"), isBlocking = value.getBoolean("blocking"),
            dependencies = value.getJSONArray("dependencies").strings(), udas = value.getJSONObject("udas").strings(),
            annotations = annotations(value.getJSONArray("annotations")),
            recurrence = if (value.isNull("recurrence")) null else recurrence(value.getJSONObject("recurrence")),
            nativeDates = value.getJSONObject("nativeDates").strings(), modified = value.nullable("modified"),
            dateModes = modes(value.getJSONObject("modes")),
        )
    }

    fun encodeRule(value: RecurrenceRule): String =
        JSONObject().put("interval", value.interval).putNullable("until", value.until).toString()

    fun decodeRule(encoded: String): RecurrenceRule =
        JSONObject(encoded).let {
            RecurrenceRule(it.getString("interval"), it.nullable("until"))
        }

    // Saving the state wrapper also distinguishes a restored null from a failed restore.
    private fun <T> stateSaver(
        encode: (T) -> String,
        decode: (String) -> T,
    ): Saver<MutableState<T>, String> =
        Saver(
            save = { encode(it.value) },
            restore = { runCatching { mutableStateOf(decode(it)) }.getOrNull() },
        )

    val taskStateSaver = stateSaver(::encodeTask, ::decodeTask)
    val nullableTaskStateSaver = stateSaver<Task?>({ it?.let(::encodeTask) ?: "" }, { if (it.isEmpty()) null else decodeTask(it) })
    val modesStateSaver = stateSaver<DateDisplayModes>({ modes(it).toString() }, { modes(JSONObject(it)) })
    val ruleStateSaver = stateSaver(::encodeRule, ::decodeRule)
    val nullableRuleStateSaver =
        stateSaver<RecurrenceRule?>({ it?.let(::encodeRule) ?: "" }, { if (it.isEmpty()) null else decodeRule(it) })
    val annotationsStateSaver = stateSaver<List<Annotation>>({ annotations(it).toString() }, { annotations(JSONArray(it)) })
}
