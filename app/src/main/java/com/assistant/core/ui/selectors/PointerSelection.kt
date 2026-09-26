package com.assistant.core.ui.selectors

import com.assistant.core.ai.enrichments.PointerConfig
import com.assistant.core.ai.enrichments.PointerKind
import com.assistant.core.ai.enrichments.PointerTarget
import com.assistant.core.ui.components.Period
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.RelativePeriod
import org.json.JSONArray
import org.json.JSONObject

/** A zone or a tool the selector went through: its id, its name as shown, a tool's type. */
data class Named(val id: String, val name: String, val tooltype: String? = null)

/**
 * The period of a pointer as its selector edits it: each bound is a period picked (a chat), a
 * relative period (an automation, resolved at each run), a date, or now.
 */
data class TimestampSelection(
    val minPeriodType: PeriodType? = null,
    val minPeriod: Period? = null,
    val minCustomDateTime: Long? = null,
    val minIsNow: Boolean = false,
    val maxPeriodType: PeriodType? = null,
    val maxPeriod: Period? = null,
    val maxCustomDateTime: Long? = null,
    val maxIsNow: Boolean = false,
    val minRelativePeriod: RelativePeriod? = null,
    val maxRelativePeriod: RelativePeriod? = null
) {
    /** The bound the period starts at, as a filter value, or null when it has none. */
    val start: Any?
        get() = when {
            minIsNow -> "NOW"
            else -> minRelativePeriod?.let { "${it.offset}_${it.type.name}" } ?: minCustomDateTime ?: minPeriod?.timestamp
        }

    /** The bound the period ends at: the last instant of a period picked, by [periodEnd]. */
    fun end(periodEnd: (Period) -> Long): Any? = when {
        maxIsNow -> "NOW"
        else -> maxRelativePeriod?.let { "${it.offset}_${it.type.name}" } ?: maxCustomDateTime ?: maxPeriod?.let(periodEnd)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        minPeriodType?.let { put("min_period_type", it.name) }
        minPeriod?.let { put("min_period", JSONObject().put("timestamp", it.timestamp).put("type", it.type.name)) }
        minCustomDateTime?.let { put("min_custom_date_time", it) }
        put("min_is_now", minIsNow)
        maxPeriodType?.let { put("max_period_type", it.name) }
        maxPeriod?.let { put("max_period", JSONObject().put("timestamp", it.timestamp).put("type", it.type.name)) }
        maxCustomDateTime?.let { put("max_custom_date_time", it) }
        put("max_is_now", maxIsNow)
        minRelativePeriod?.let { put("min_relative_period", JSONObject().put("offset", it.offset).put("type", it.type.name)) }
        maxRelativePeriod?.let { put("max_relative_period", JSONObject().put("offset", it.offset).put("type", it.type.name)) }
    }

    companion object {
        fun fromJson(json: JSONObject): TimestampSelection {
            fun period(key: String) = json.optJSONObject(key)?.let { Period(it.getLong("timestamp"), PeriodType.valueOf(it.getString("type"))) }
            fun relative(key: String) = json.optJSONObject(key)?.let { RelativePeriod(it.getInt("offset"), PeriodType.valueOf(it.getString("type"))) }
            fun long(key: String) = if (json.has(key) && !json.isNull(key)) json.getLong(key) else null
            fun type(key: String) = json.optString(key).takeIf { it.isNotEmpty() }?.let { PeriodType.valueOf(it) }
            return TimestampSelection(
                minPeriodType = type("min_period_type"),
                minPeriod = period("min_period"),
                minCustomDateTime = long("min_custom_date_time"),
                minIsNow = json.optBoolean("min_is_now", false),
                maxPeriodType = type("max_period_type"),
                maxPeriod = period("max_period"),
                maxCustomDateTime = long("max_custom_date_time"),
                maxIsNow = json.optBoolean("max_is_now", false),
                minRelativePeriod = relative("min_relative_period"),
                maxRelativePeriod = relative("max_relative_period")
            )
        }
    }
}

/**
 * What the pointer selector holds while a pointer is built: where the user went (a zone, a tool
 * in it), what is attached, and what narrows the tool's entries.
 *
 * The target is the deepest place reached. A zone offers its config; a tool its config and its
 * entries, with a period, value filters and a choice of fields, which narrow the entries whether
 * they are attached or only mentioned.
 *
 * @property filters The value filters, as tool_data.get takes them; the period apart
 * @property fields The fields of the entries to attach, all of them when null
 */
data class PointerSelection(
    val zone: Named? = null,
    val tool: Named? = null,
    val config: Boolean = false,
    val entries: Boolean = false,
    val period: TimestampSelection = TimestampSelection(),
    val filters: JSONArray = JSONArray(),
    val fields: List<String>? = null
) {
    /** The kind of the deepest place reached: the app until a zone is chosen. */
    val level: PointerKind get() = when {
        tool != null -> PointerKind.TOOL
        zone != null -> PointerKind.ZONE
        else -> PointerKind.APP
    }

    /** A pointer can be made once a zone or a tool is reached. */
    val complete: Boolean get() = level == PointerKind.ZONE || level == PointerKind.TOOL

    /** Whether the entries are narrowed, by a period or a value filter. */
    val narrowed: Boolean get() = period.start != null || period.end { it.timestamp } != null || filters.length() > 0

    /** Into [zone]: what only a tool offers goes, and so does anything chosen in another zone. */
    fun intoZone(zone: Named): PointerSelection = PointerSelection(zone = zone, config = config)

    /** Into [tool]: its entries' period, filters and fields start empty. */
    fun intoTool(tool: Named): PointerSelection = copy(tool = tool, period = TimestampSelection(), filters = JSONArray(), fields = null)

    /** Back up to the app, or to the zone: what the level left offered goes with it. */
    fun upTo(level: PointerKind): PointerSelection = when (level) {
        PointerKind.APP -> PointerSelection()
        PointerKind.ZONE -> PointerSelection(zone = zone, config = config)
        else -> this
    }

    /**
     * The pointer to store: the period's bounds as filters on timestamp, before the value
     * filters. [periodEnd] gives the last instant of a period picked.
     */
    fun pointer(periodEnd: (Period) -> Long): PointerConfig {
        val target = when (level) {
            PointerKind.TOOL -> PointerTarget(PointerKind.TOOL, tool!!.id)
            PointerKind.ZONE -> PointerTarget(PointerKind.ZONE, zone!!.id)
            else -> throw IllegalStateException("a pointer needs a zone or a tool")
        }
        val all = JSONArray()
        if (level == PointerKind.TOOL) {
            period.start?.let { all.put(JSONObject().put("field", "timestamp").put("op", ">=").put("value", it)) }
            period.end(periodEnd)?.let { all.put(JSONObject().put("field", "timestamp").put("op", "<=").put("value", it)) }
            for (i in 0 until filters.length()) all.put(filters.get(i))
        }
        return PointerConfig(
            target = target,
            config = config,
            entries = entries && level == PointerKind.TOOL,
            filters = all,
            fields = fields.takeIf { level == PointerKind.TOOL }
        )
    }

    fun toJson(): String = JSONObject().apply {
        zone?.let { put("zone", named(it)) }
        tool?.let { put("tool", named(it)) }
        put("config", config)
        put("entries", entries)
        put("period", period.toJson())
        put("filters", filters)
        fields?.let { put("fields", JSONArray(it)) }
    }.toString()

    companion object {
        private fun named(n: Named) = JSONObject().put("id", n.id).put("name", n.name).apply { n.tooltype?.let { put("tooltype", it) } }
        private fun named(json: JSONObject) = Named(json.getString("id"), json.getString("name"), json.optString("tooltype").takeIf { it.isNotEmpty() })

        fun fromJson(saved: String): PointerSelection {
            val json = JSONObject(saved)
            return PointerSelection(
                zone = json.optJSONObject("zone")?.let { named(it) },
                tool = json.optJSONObject("tool")?.let { named(it) },
                config = json.getBoolean("config"),
                entries = json.getBoolean("entries"),
                period = TimestampSelection.fromJson(json.getJSONObject("period")),
                filters = json.getJSONArray("filters"),
                fields = json.optJSONArray("fields")?.let { a -> (0 until a.length()).map { a.getString(it) } }
            )
        }
    }
}
