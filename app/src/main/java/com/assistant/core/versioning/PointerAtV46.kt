package com.assistant.core.versioning

import android.content.Context
import com.assistant.core.fields.EntryFilters
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.toFieldDefinitions
import com.assistant.core.tools.ToolTypeManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the POINTER enrichments stored in messages to their v46 form: a selection of the core
 * under its own key, `{"selection": {"target", "period", "filters", "fields"}, "attach"}`.
 *
 * At v45 a pointer held its target (`TOOL` for a tool instance), its period as filters on
 * timestamp, and each relative date as a string ("-7_DAY", "NOW") whose edge the condition
 * chose: `>=` and `<` the start, `>` and `<=` the end, `=` the whole of it. They become:
 * - the target, `TOOL` renamed `TOOL_INSTANCE`, under `selection` with the fields kept;
 * - the bounds on timestamp, the period: `>=` its start, `<=` its end, `between` both, `=` on a
 *   relative date its start and its end; any other condition on timestamp stays a filter;
 * - each relative date on a date field, an object with the edge its condition took, an `=`
 *   becoming a `between` from its start to its end. A fixed date does not change.
 * Which field is a date is read from the tool's config ([fieldTypes]), never from the look of a
 * value; the filters of a tool deleted since are left as they are, the pointer reading as deleted.
 *
 * Shared by the database migration and the backup import.
 */
object PointerAtV46 {

    private val RELATIVE = Regex("^(-?\\d+)_(HOUR|DAY|WEEK|MONTH|YEAR)$")

    /**
     * [old] as a v46 pointer, or null when it has that form already.
     *
     * @param fieldTypes The type of each filterable field of a tool instance, by path; null for a
     *   tool that is no more
     * @throws IllegalArgumentException when it has no target
     */
    fun config(old: JSONObject, fieldTypes: (String) -> Map<String, FieldType>?): JSONObject? {
        if (old.has("selection")) return null

        val oldTarget = old.optJSONObject("target") ?: throw IllegalArgumentException("pointer without a target")
        val kind = oldTarget.getString("kind").let { if (it == "TOOL") "TOOL_INSTANCE" else it }
        val target = JSONObject().put("kind", kind).apply { oldTarget.optString("id").takeIf { it.isNotEmpty() }?.let { put("id", it) } }
        val types: Map<String, FieldType> = if (kind == "TOOL_INSTANCE") fieldTypes(target.getString("id")) ?: emptyMap() else emptyMap()

        val period = JSONObject()
        val filters = JSONArray()
        val oldFilters = old.optJSONArray("filters") ?: JSONArray()
        for (i in 0 until oldFilters.length()) {
            val filter = oldFilters.getJSONObject(i)
            val path = filter.optString("field")
            if (path == "timestamp" && intoPeriod(filter, period)) continue
            val type = if (path == "timestamp") FieldType.DATETIME else types[path]
            filters.put(if (type == FieldType.DATE || type == FieldType.DATETIME) dateFilter(filter) else filter)
        }

        val selection = JSONObject().put("target", target).apply {
            if (period.length() > 0) put("period", period)
            if (filters.length() > 0) put("filters", filters)
            old.optJSONArray("fields")?.let { put("fields", it) }
        }
        return JSONObject()
            .put("selection", selection)
            .put("attach", old.optJSONObject("attach") ?: JSONObject().put("config", false).put("entries", false))
    }

    /** [value] as stored at v46: a relative date as its object on [edge], anything else as it is. */
    private fun point(value: Any?, edge: String): Any? = when {
        value == "NOW" -> JSONObject().put("relative", "NOW")
        value is String && RELATIVE.matches(value) -> RELATIVE.find(value)!!.groupValues.let { (_, offset, unit) ->
            JSONObject().put("relative", JSONObject().put("unit", unit).put("offset", offset.toInt()).put("edge", edge))
        }
        else -> value
    }

    private fun isRelative(value: Any?) = value == "NOW" || (value is String && RELATIVE.matches(value))

    /**
     * Moves a bound on timestamp into [period], unless the bound it sets is taken already or the
     * condition is no bound: then it stays a filter, and this says false.
     */
    private fun intoPeriod(filter: JSONObject, period: JSONObject): Boolean {
        val value = filter.opt("value")
        val bounds: List<Pair<String, Any?>> = when (filter.optString("op")) {
            ">=" -> listOf("start" to point(value, "START"))
            "<=" -> listOf("end" to point(value, "END"))
            "between" -> (value as? JSONArray)?.takeIf { it.length() == 2 }
                ?.let { listOf("start" to point(it.get(0), "START"), "end" to point(it.get(1), "END")) }
                ?: return false
            "=" -> if (isRelative(value) && value != "NOW") listOf("start" to point(value, "START"), "end" to point(value, "END")) else return false
            else -> return false
        }
        if (bounds.any { (key, _) -> period.has(key) }) return false
        bounds.forEach { (key, bound) -> period.put(key, bound) }
        return true
    }

    /** A filter on a date field with its relative dates stored as objects on the edge the condition took. */
    private fun dateFilter(filter: JSONObject): JSONObject {
        val next = JSONObject(filter.toString())
        val value = filter.opt("value")
        when (filter.optString("op")) {
            ">=", "<" -> next.put("value", point(value, "START"))
            ">", "<=" -> next.put("value", point(value, "END"))
            "between" -> (value as? JSONArray)?.takeIf { it.length() == 2 }?.let {
                next.put("value", JSONArray().put(point(it.get(0), "START")).put(point(it.get(1), "END")))
            }
            // Equal to now is a point; equal to a relative period, the whole of it
            "=" -> when {
                value == "NOW" -> next.put("value", point(value, "START"))
                isRelative(value) -> next.put("op", "between").put("value", JSONArray().put(point(value, "START")).put(point(value, "END")))
            }
        }
        return next
    }

    /**
     * A message's rich content with its pointers at v46, or null when none changed. A pointer
     * that cannot be read is left as it was and reported through [onFailure].
     */
    fun richContent(json: String, fieldTypes: (String) -> Map<String, FieldType>?, onFailure: (Exception) -> Unit): String? {
        val content = JSONObject(json)
        val segments = content.optJSONArray("segments") ?: return null
        var changed = false
        for (i in 0 until segments.length()) {
            val segment = segments.getJSONObject(i)
            if (segment.optString("type") != "enrichment" || segment.optString("enrichment_type") != "POINTER") continue
            try {
                val next = config(JSONObject(segment.getString("config")), fieldTypes) ?: continue
                segment.put("config", next.toString())
                changed = true
            } catch (e: Exception) {
                onFailure(e)
            }
        }
        return if (changed) content.toString() else null
    }

    /** The type of each filterable field of a tool of [tooltype] with [configJson], by path. */
    fun fieldTypes(tooltype: String, configJson: String, context: Context): Map<String, FieldType> {
        val toolType = ToolTypeManager.getToolType(tooltype) ?: throw IllegalArgumentException("unknown tooltype $tooltype")
        val config = JSONObject(configJson)
        val extra = config.optJSONArray("extra_fields")?.toFieldDefinitions() ?: emptyList()
        // The labels do not matter here, only the types
        return EntryFilters.filterableFields(toolType.getEntryFields(config, context), extra) { it }.mapValues { it.value.type }
    }

    /** Rewrites the pointers of the backup document's messages in place. */
    fun backup(data: JSONObject, context: Context) {
        val messages = data.optJSONArray("session_messages") ?: return
        val tools = data.optJSONArray("tool_instances")?.let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }.associateBy { it.getString("id") }
        } ?: emptyMap()
        val fieldTypes = { id: String -> tools[id]?.let { fieldTypes(it.getString("tooltype"), it.getString("config_json"), context) } }
        for (i in 0 until messages.length()) {
            val message = messages.getJSONObject(i)
            if (message.isNull("rich_content_json")) continue
            richContent(message.getString("rich_content_json"), fieldTypes) { e ->
                com.assistant.core.utils.LogManager.database("Backup import: a pointer of message ${message.optString("id")} left as it was: ${e.message}", "ERROR", e)
            }?.let { message.put("rich_content_json", it) }
        }
    }
}
