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
 * The target is the deepest place reached. A zone offers its config and the entries of its tools
 * over a period; a tool its config and its entries, with a period, value filters and a choice of
 * fields, which narrow the entries whether they are attached or only mentioned. Value filters and
 * fields are a tool's own: they have no sense across the tools of a zone.
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
    val narrowed: Boolean get() = periodFilters("timestamp", period, { it.timestamp }, day = null).length() > 0 || filters.length() > 0

    /** Into [zone]: the boxes and the period stay, which a zone offers as well. */
    fun intoZone(zone: Named): PointerSelection = PointerSelection(zone = zone, config = config, entries = entries, period = period)

    /** Into [tool]: the period stays; its filters and fields, a tool's own, start empty. */
    fun intoTool(tool: Named): PointerSelection = copy(tool = tool, filters = JSONArray(), fields = null)

    /** Back up to the app, or to the zone: what the level left offered goes with it. */
    fun upTo(level: PointerKind): PointerSelection = when (level) {
        PointerKind.APP -> PointerSelection()
        PointerKind.ZONE -> PointerSelection(zone = zone, config = config, entries = entries, period = period)
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
        val bounds = periodFilters("timestamp", period, periodEnd, day = null)
        for (i in 0 until bounds.length()) all.put(bounds.get(i))
        if (level == PointerKind.TOOL) {
            for (i in 0 until filters.length()) all.put(filters.get(i))
        }
        return PointerConfig(
            target = target,
            config = config,
            entries = entries,
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

private const val DAY_MILLIS = 86_400_000L

/**
 * A period on the date field [path] as filters: ">=" its start and "<=" its end, each only when
 * set. "NOW" and a relative period stay as they are, resolved at each send; a date or a period
 * picked is in milliseconds, or for a DATE field ([day] given) the day it falls on: the last day
 * of a period picked is the one before the next period starts.
 */
fun periodFilters(path: String, period: TimestampSelection, periodEnd: (Period) -> Long, day: ((Long) -> String)?): JSONArray {
    fun fixed(millis: Long): Any = day?.invoke(millis) ?: millis
    val start: Any? = when {
        period.minIsNow -> "NOW"
        period.minRelativePeriod != null -> period.minRelativePeriod.let { "${it.offset}_${it.type.name}" }
        period.minCustomDateTime != null -> fixed(period.minCustomDateTime)
        period.minPeriod != null -> fixed(period.minPeriod.timestamp)
        else -> null
    }
    val end: Any? = when {
        period.maxIsNow -> "NOW"
        period.maxRelativePeriod != null -> period.maxRelativePeriod.let { "${it.offset}_${it.type.name}" }
        period.maxCustomDateTime != null -> fixed(period.maxCustomDateTime)
        period.maxPeriod != null -> periodEnd(period.maxPeriod).let { last -> if (day != null) day(last + 1 - DAY_MILLIS) else last }
        else -> null
    }
    return JSONArray().apply {
        start?.let { put(JSONObject().put("field", path).put("op", ">=").put("value", it)) }
        end?.let { put(JSONObject().put("field", path).put("op", "<=").put("value", it)) }
    }
}
