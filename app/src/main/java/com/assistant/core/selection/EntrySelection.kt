package com.assistant.core.selection

import org.json.JSONArray
import org.json.JSONObject

/**
 * A period as two bounds, each absent for no limit: `{"start": …, "end": …}`, both inclusive.
 * Neither bound is a filter: a period is said apart from the filters on values.
 */
data class EntryPeriod(val start: TimePoint? = null, val end: TimePoint? = null) {

    val isEmpty: Boolean get() = start == null && end == null

    fun toJson(): JSONObject = JSONObject().apply {
        start?.let { put("start", it.toJson()) }
        end?.let { put("end", it.toJson()) }
    }

    /**
     * The period as the filters of tool_data.get on timestamp: ">=" its start, "<=" its end,
     * resolved by [resolver].
     */
    fun timestampFilters(resolver: TimeResolver): List<Map<String, Any>> = listOfNotNull(
        start?.let { mapOf("field" to "timestamp", "op" to ">=", "value" to resolver.instant(it)) },
        end?.let { mapOf("field" to "timestamp", "op" to "<=", "value" to resolver.instant(it)) }
    )

    companion object {
        /** @throws IllegalArgumentException on a bound that does not read */
        fun fromJson(json: JSONObject, text: (String) -> String): EntryPeriod = EntryPeriod(
            start = json.opt("start")?.let { TimePoint.read(it, text) },
            end = json.opt("end")?.let { TimePoint.read(it, text) }
        )
    }
}

/**
 * Entries of the app: a thing (the reference), the period their timestamp falls in, the filters
 * on their values, and the fields kept. The core's own notion, read the same wherever it serves:
 * a message's pointer, a chart, a reading, a variable's term. Stored as
 * `{"target", "period", "filters", "fields"}`, the last three absent when they narrow nothing.
 *
 * Filters and fields are an instance's own; a period narrows an instance's entries, or a zone's,
 * applied to each of its tools. [problem] names what does not fit its target.
 *
 * @property filters The filters of tool_data.get, a date's value being a TimePoint's stored form
 * @property fields The fields kept, all of them when null
 */
data class EntrySelection(
    val target: Reference,
    val period: EntryPeriod = EntryPeriod(),
    val filters: JSONArray = JSONArray(),
    val fields: List<String>? = null
) {

    /** What the target does not take, in words for the log and the AI; null when it all fits. */
    fun problem(): String? = when {
        target.kind == ReferenceKind.TOOL_INSTANCE -> null
        filters.length() > 0 || fields != null -> "filters and fields narrow the entries of a tool instance, not of a ${target.kind}"
        !period.isEmpty && target.kind != ReferenceKind.ZONE -> "a period narrows the entries of a tool instance or a zone, not of a ${target.kind}"
        else -> null
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("target", target.toJson())
        if (!period.isEmpty) put("period", period.toJson())
        if (filters.length() > 0) put("filters", filters)
        fields?.let { put("fields", JSONArray(it)) }
    }

    companion object {
        /** @throws IllegalArgumentException on a period bound that does not read */
        fun fromJson(json: JSONObject, text: (String) -> String): EntrySelection = EntrySelection(
            target = Reference.fromJson(json.getJSONObject("target")),
            period = json.optJSONObject("period")?.let { EntryPeriod.fromJson(it, text) } ?: EntryPeriod(),
            filters = json.optJSONArray("filters") ?: JSONArray(),
            fields = json.optJSONArray("fields")?.let { array -> (0 until array.length()).map { array.getString(it) } }
        )
    }
}
