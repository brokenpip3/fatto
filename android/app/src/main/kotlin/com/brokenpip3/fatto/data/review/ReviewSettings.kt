package com.brokenpip3.fatto.data.review

import org.json.JSONObject
import java.time.Duration

enum class ReviewAction(val label: String) {
    REVIEWED("Mark reviewed"),
    SKIP("Skip"),
    COMPLETE("Complete"),
    DELETE("Delete"),
}

data class ReviewSettings(
    val intervalDays: Int = 6,
    val sessionLimit: Int? = null,
    val project: String? = null,
    val tag: String? = null,
    val rightAction: ReviewAction = ReviewAction.REVIEWED,
    val leftAction: ReviewAction = ReviewAction.SKIP,
    val reminderDays: Int? = null,
) {
    init {
        require(intervalDays > 0) { "Enter a positive number of days" }
        require(sessionLimit == null || sessionLimit > 0) { "Enter a positive task limit or leave blank" }
        require(reminderDays == null || reminderDays > 0) { "Enter a positive reminder interval" }
        Duration.ofDays(intervalDays.toLong()).toMillis()
        reminderDays?.let { Duration.ofDays(it.toLong()).toMillis() }
    }

    fun toJson(): JSONObject =
        JSONObject()
            .put("intervalDays", intervalDays).put("sessionLimit", sessionLimit ?: JSONObject.NULL)
            .put("project", project ?: JSONObject.NULL).put("tag", tag ?: JSONObject.NULL)
            .put("rightAction", rightAction.name).put("leftAction", leftAction.name)
            .put("reminderDays", reminderDays ?: JSONObject.NULL)

    companion object {
        private fun positiveInt(
            json: JSONObject,
            key: String,
        ): Int {
            val raw = json.get(key)
            require(raw is Number) { "$key must be a positive whole number" }
            val value = raw.toString().toBigDecimal().intValueExact()
            require(value > 0) { "$key must be positive" }
            return value
        }

        fun fromJson(json: JSONObject): ReviewSettings =
            ReviewSettings(
                intervalDays = positiveInt(json, "intervalDays"),
                sessionLimit = if (json.isNull("sessionLimit")) null else positiveInt(json, "sessionLimit"),
                project = if (json.isNull("project")) null else json.getString("project"),
                tag = if (json.isNull("tag")) null else json.getString("tag"),
                rightAction = ReviewAction.valueOf(json.getString("rightAction")),
                leftAction = ReviewAction.valueOf(json.getString("leftAction")),
                reminderDays = if (json.isNull("reminderDays")) null else positiveInt(json, "reminderDays"),
            )
    }
}
