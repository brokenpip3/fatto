package com.brokenpip3.fatto.data

import android.util.Log
import com.brokenpip3.fatto.data.filter.TaskFilterExpressionFormatter
import com.brokenpip3.fatto.data.model.TaskContext
import org.json.JSONArray
import org.json.JSONObject

object TaskContextCodec {
    fun encode(contexts: List<TaskContext>): String {
        val array = JSONArray()
        contexts.forEach { context ->
            array.put(
                JSONObject()
                    .put("id", context.id)
                    .put("name", context.name)
                    .put("expressionText", context.expressionText),
            )
        }
        return array.toString()
    }

    fun decode(value: String?): List<TaskContext> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index)
                    if (item != null) {
                        val decoded = runCatching { decodeContext(item) }.getOrNull()
                        if (decoded != null) add(decoded)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TaskContextCodec", "Failed to decode task contexts", e)
            emptyList()
        }
    }

    private fun decodeContext(item: JSONObject): TaskContext? {
        val id = item.optString("id")
        val name = item.optString("name")
        if (id.isBlank() || name.isBlank()) return null

        val expressionText =
            when {
                item.has("expressionText") ->
                    item.optString("expressionText", "")

                else ->
                    buildLegacyExpressionText(
                        descriptionQuery = item.optString("descriptionQuery", ""),
                        project = decodeLegacyProject(item),
                        tags = decodeLegacyTags(item.optJSONArray("tags")),
                    )
            }
        return TaskContext(
            id = id,
            name = name,
            expressionText = expressionText,
        )
    }

    private fun decodeLegacyProject(item: JSONObject): String? {
        if (!item.has("project") || item.isNull("project")) return null
        val project = item.optString("project", "")
        return project.takeIf { it.isNotBlank() }
    }

    private fun decodeLegacyTags(tagsArray: JSONArray?): List<String> {
        if (tagsArray == null) return emptyList()
        return buildList {
            for (index in 0 until tagsArray.length()) {
                val tag = tagsArray.optString(index, "").trim()
                if (tag.isNotBlank()) add(tag)
            }
        }.sorted()
    }

    private fun buildLegacyExpressionText(
        descriptionQuery: String,
        project: String?,
        tags: List<String>,
    ): String =
        buildList {
            if (descriptionQuery.isNotBlank()) add(TaskFilterExpressionFormatter.keyword(descriptionQuery))
            if (!project.isNullOrBlank()) add(TaskFilterExpressionFormatter.term("project:$project"))
            tags.forEach { add(TaskFilterExpressionFormatter.term("+$it")) }
        }.joinToString(" ")
}
