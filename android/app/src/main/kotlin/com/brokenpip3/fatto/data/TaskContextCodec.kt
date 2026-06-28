package com.brokenpip3.fatto.data

import android.util.Log
import com.brokenpip3.fatto.data.model.TaskContext
import org.json.JSONArray
import org.json.JSONObject

object TaskContextCodec {
    fun encode(contexts: List<TaskContext>): String {
        val array = JSONArray()
        contexts.forEach { context ->
            val tags = JSONArray()
            context.tags.sorted().forEach { tags.put(it) }
            array.put(
                JSONObject()
                    .put("id", context.id)
                    .put("name", context.name)
                    .put("descriptionQuery", context.descriptionQuery)
                    .put("project", context.project)
                    .put("tags", tags),
            )
        }
        return array.toString()
    }

    fun decode(value: String?): List<TaskContext> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val tagsArray = item.optJSONArray("tags") ?: JSONArray()
                val tags = List(tagsArray.length()) { tagIndex -> tagsArray.getString(tagIndex) }.toSet()
                TaskContext(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    descriptionQuery = item.optString("descriptionQuery"),
                    project =
                        if (item.isNull("project")) {
                            null
                        } else {
                            item.optString("project").takeIf { it.isNotBlank() }
                        },
                    tags = tags,
                )
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        } catch (e: Exception) {
            Log.e("TaskContextCodec", "Failed to decode task contexts", e)
            emptyList()
        }
    }
}
