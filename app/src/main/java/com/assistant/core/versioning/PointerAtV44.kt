package com.assistant.core.versioning

import com.assistant.core.config.DateTimeConfig
import com.assistant.core.config.FormatDefaults
import com.assistant.core.database.entities.AppSettingCategories
import com.assistant.core.ui.components.Period
import com.assistant.core.ui.components.PeriodType
import com.assistant.core.ui.components.getPeriodEndTimestamp
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

/**
 * Brings the POINTER enrichments stored in messages to their v44 form: a target
 * named by its kind and id, what is attached as two booleans, and the period as filters on
 * timestamp.
 *
 * Before, a pointer held a path ("tools.<id>", "zones.<id>"), a context (GENERIC, CONFIG, DATA)
 * with the resources ticked in it, and a period in its own shape. They become:
 * - CONFIG with "config" ticked: the config attached; DATA with "data" ticked: the entries. A
 *   schema ticked alone attaches nothing: the schema now goes with what it describes, so such a
 *   pointer becomes a mention, from which the AI asks what it needs;
 * - each bound of the period, a filter: "NOW" and a relative period as they were, resolved at each
 *   send; a date as its milliseconds; a period picked in a chat from its first instant to its last,
 *   which the user's day start, week start and timezone decide ([Calendar]).
 *
 * Shared by the database migration and the backup import.
 */
object PointerAtV44 {

    /** What the end of a period depends on, read from the format settings. */
    data class Calendar(val zone: ZoneId, val dayStartHour: Int, val weekStartDay: String) {
        companion object {
            /** From the format settings as stored, the defaults for what they leave out. */
            fun of(format: JSONObject?): Calendar = Calendar(
                zone = DateTimeConfig(timezoneOverride = format?.optString("timezone_override")?.takeIf { it.isNotEmpty() }).getZoneId(),
                dayStartHour = format?.optInt("day_start_hour", FormatDefaults.DAY_START_HOUR) ?: FormatDefaults.DAY_START_HOUR,
                weekStartDay = (format?.optString("week_start_day")?.takeIf { it.isNotEmpty() } ?: FormatDefaults.WEEK_START_DAY).uppercase()
            )
        }
    }

    /**
     * [old] as a v44 pointer, or null when it has that form already.
     *
     * @throws IllegalArgumentException when its path names neither a zone nor a tool
     */
    fun config(old: JSONObject, calendar: Calendar): JSONObject? {
        if (old.has("target")) return null

        val path = old.optString("selected_path").split(".")
        val target = when {
            old.optString("selection_level") == "ZONE" && path.size == 2 && path[0] == "zones" -> JSONObject().put("kind", "ZONE").put("id", path[1])
            old.optString("selection_level") == "INSTANCE" && path.size == 2 && path[0] == "tools" -> JSONObject().put("kind", "TOOL").put("id", path[1])
            else -> throw IllegalArgumentException("pointer path '${old.optString("selected_path")}' names neither a zone nor a tool")
        }

        val resources = old.optJSONArray("selected_resources")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
        val context = old.optString("selected_context")

        val filters = old.optJSONObject("timestamp_selection")?.let { period(it, calendar) } ?: JSONArray()
        return JSONObject()
            .put("target", target)
            .put("attach", JSONObject()
                .put("config", context == "CONFIG" && "config" in resources)
                .put("entries", context == "DATA" && "data" in resources))
            .apply { if (filters.length() > 0) put("filters", filters) }
    }

    /** The bounds of a pointer's period as filters on timestamp, from its first instant to its last. */
    private fun period(selection: JSONObject, calendar: Calendar): JSONArray {
        fun relative(key: String): String? = selection.optJSONObject(key)?.let { "${it.getInt("offset")}_${it.getString("type")}" }
        fun custom(key: String): Long? = if (selection.has(key) && !selection.isNull(key)) selection.getLong(key) else null
        fun picked(key: String, end: Boolean): Long? = selection.optJSONObject(key)?.let {
            val period = Period(it.getLong("timestamp"), PeriodType.valueOf(it.getString("type")))
            if (end) getPeriodEndTimestamp(period, calendar.dayStartHour, calendar.weekStartDay, calendar.zone) else period.timestamp
        }

        val start: Any? = when {
            selection.optBoolean("min_is_now", false) -> "NOW"
            else -> relative("min_relative_period") ?: custom("min_custom_date_time") ?: picked("min_period", end = false)
        }
        val end: Any? = when {
            selection.optBoolean("max_is_now", false) -> "NOW"
            else -> relative("max_relative_period") ?: custom("max_custom_date_time") ?: picked("max_period", end = true)
        }

        return JSONArray().apply {
            start?.let { put(JSONObject().put("field", "timestamp").put("op", ">=").put("value", it)) }
            end?.let { put(JSONObject().put("field", "timestamp").put("op", "<=").put("value", it)) }
        }
    }

    /**
     * A message's rich content with its pointers at v44, or null when none changed. A pointer
     * that cannot be read is left as it was and reported through [onFailure].
     */
    fun richContent(json: String, calendar: Calendar, onFailure: (Exception) -> Unit): String? {
        val content = JSONObject(json)
        val segments = content.optJSONArray("segments") ?: return null
        var changed = false
        for (i in 0 until segments.length()) {
            val segment = segments.getJSONObject(i)
            if (segment.optString("type") != "enrichment" || segment.optString("enrichment_type") != "POINTER") continue
            try {
                val next = config(JSONObject(segment.getString("config")), calendar) ?: continue
                segment.put("config", next.toString())
                changed = true
            } catch (e: Exception) {
                onFailure(e)
            }
        }
        return if (changed) content.toString() else null
    }

    /** The format settings among the categories of a backup document, if it holds them. */
    private fun backupFormat(data: JSONObject): JSONObject? {
        val categories = data.optJSONArray("app_settings_categories") ?: return null
        return (0 until categories.length()).map { categories.getJSONObject(it) }
            .firstOrNull { it.optString("category") == AppSettingCategories.FORMAT }
            ?.let { JSONObject(it.getString("settings")) }
    }

    /** Rewrites the pointers of the backup document's messages in place. */
    fun backup(data: JSONObject) {
        val messages = data.optJSONArray("session_messages") ?: return
        val calendar = Calendar.of(backupFormat(data))
        for (i in 0 until messages.length()) {
            val message = messages.getJSONObject(i)
            if (message.isNull("rich_content_json")) continue
            richContent(message.getString("rich_content_json"), calendar) { e ->
                com.assistant.core.utils.LogManager.database("Backup import: a pointer of message ${message.optString("id")} left as it was: ${e.message}", "ERROR", e)
            }?.let { message.put("rich_content_json", it) }
        }
    }
}
