package com.assistant.tools.tracking

import org.json.JSONArray
import org.json.JSONObject

/**
 * A shortcut of a tracking tool: a name given to the entries it creates, and for a numeric or
 * counter tool the value it enters (for a counter, the amount added or taken away) and for a
 * numeric one its unit.
 */
data class TrackingShortcut(
    val name: String,
    val value: Number? = null,
    val unit: String? = null
)

/**
 * What a tracking tool's config says beyond its field settings: its shortcuts, its units, and
 * whether a counter can go down. Read the same way by every screen.
 */
object TrackingConfig {

    fun shortcuts(config: JSONObject): List<TrackingShortcut> {
        val items = config.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            TrackingShortcut(
                name = item.getString("name"),
                value = if (item.has("value")) item.get("value") as Number else null,
                unit = item.optString("unit").takeIf { it.isNotEmpty() }
            )
        }
    }

    /** The units a numeric value can be in, in the order the config lists them. */
    fun units(config: JSONObject): List<String> {
        val units = config.optJSONArray("units") ?: return emptyList()
        return (0 until units.length()).map { units.getString(it) }
    }

    /** Whether a counter's shortcuts also take away; they do unless the config says otherwise. */
    fun allowsDecrement(config: JSONObject, context: android.content.Context): Boolean =
        com.assistant.core.tools.ToolConfigSettings.read(TrackingToolType, config, context).boolean("allow_decrement")

    /** The amount a counter shortcut adds or takes away: its value, 1 when it has none. */
    fun counterStep(shortcut: TrackingShortcut): Int = shortcut.value?.toInt()?.takeIf { it != 0 }?.let { Math.abs(it) } ?: 1

    /** [config] with [shortcut] appended to its shortcuts. */
    fun withShortcut(config: JSONObject, shortcut: TrackingShortcut): JSONObject {
        val next = JSONObject(config.toString())
        val items = next.optJSONArray("items") ?: JSONArray()
        items.put(JSONObject().apply {
            put("name", shortcut.name)
            shortcut.value?.let { put("value", it) }
            shortcut.unit?.let { put("unit", it) }
        })
        next.put("items", items)
        return next
    }

    /**
     * The data of an entry holding [value], and for a numeric tool [unit]: what a shortcut or
     * the entry dialog writes. An occurrence has no value, and a timer none until it stops.
     */
    fun entryData(value: Any?, unit: String?): JSONObject = JSONObject().apply {
        value?.let { put("value", if (it is List<*>) JSONArray(it) else it) }
        unit?.let { put("unit", it) }
    }
}
