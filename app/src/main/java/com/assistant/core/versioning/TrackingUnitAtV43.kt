package com.assistant.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings a numeric tracking tool to its v43 form, where its units live in one place: the "units"
 * list, from which each entry takes its own ("unit" in its data). A unit set in the value settings
 * ("value.unit") leaves them and heads the list, unless the list already holds it.
 *
 * The entries of such a tool that record no unit showed that one next to their value: they take it,
 * so they keep saying what they measured.
 *
 * Shared by the database migration and the backup import.
 */
object TrackingUnitAtV43 {

    /** The unit [config] of a [tooltype] tool sets in its value settings, if any. */
    fun valueUnit(tooltype: String, config: JSONObject): String? {
        if (tooltype != "tracking" || config.optString("type") != "numeric") return null
        return config.optJSONObject("value")?.optString("unit")?.takeIf { it.isNotEmpty() }
    }

    /** [config] of a [tooltype] tool, as it stands at v43. */
    fun config(tooltype: String, config: JSONObject): JSONObject {
        if (tooltype != "tracking") return config
        val next = JSONObject(config.toString())
        val value = next.optJSONObject("value") ?: return next
        val unit = valueUnit(tooltype, next)
        value.remove("unit")
        if (unit != null) {
            val units = next.optJSONArray("units") ?: JSONArray()
            val listed = (0 until units.length()).map { units.getString(it) }
            if (unit !in listed) next.put("units", JSONArray(listOf(unit) + listed))
        }
        return next
    }

    /** An entry's [data] of a tool whose value settings set [unit]: with a unit. */
    fun entryData(data: JSONObject, unit: String): JSONObject {
        if (data.optString("unit").isNotEmpty()) return data
        return JSONObject(data.toString()).put("unit", unit)
    }

    /** Rewrites the backup document's tool configs and their entries in place. */
    fun backup(data: JSONObject) {
        val instances = data.optJSONArray("tool_instances") ?: return
        val units = mutableMapOf<String, String>()
        for (i in 0 until instances.length()) {
            val instance = instances.getJSONObject(i)
            val tooltype = instance.getString("tooltype")
            val config = JSONObject(instance.getString("config_json"))
            valueUnit(tooltype, config)?.let { units[instance.getString("id")] = it }
            instance.put("config_json", config(tooltype, config).toString())
        }
        val entries = data.optJSONArray("tool_data") ?: return
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val unit = units[entry.getString("tool_instance_id")] ?: continue
            entry.put("data", entryData(JSONObject(entry.getString("data")), unit).toString())
        }
    }
}
