package com.assistant.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings tool configs and entries to their v36 form, the one field system.
 *
 * - A config's "custom_fields" (the user's field definitions) becomes "extra_fields".
 * - A tracking tool's config and entries take the shape of the field types: see trackingConfig
 *   and trackingEntry.
 * - An entry's "custom_fields" column (the user's values) becomes "extra", and an entry gains a
 *   "state" object: what the app and its actions produce on it, moved out of "data" (a note's
 *   position; a message occurrence's status, delivery, read, archived and origin).
 *
 * An entry is rewritten with its tool's config as it stood at v35, since what a tracking entry
 * becomes depends on its tool's type. This is the single place that says so, shared by the
 * database migration and the backup import.
 */
object FieldsAtV36 {

    /** The columns of an entry this rewrite touches. Null objects are absent columns. */
    data class Entry(
        val name: String?,
        val data: JSONObject,
        val extra: JSONObject?,
        val state: JSONObject?
    )

    /**
     * @param entriesData The data of the tool's entries at v35: a numeric tracking tool's units
     *        are the units its shortcuts and its entries use
     * @return [config] of a [tooltype] tool, as it stands at v36
     */
    fun config(tooltype: String, config: JSONObject, entriesData: List<JSONObject>): JSONObject {
        val next = JSONObject(config.toString())
        if (next.has("custom_fields")) {
            next.put("extra_fields", next.get("custom_fields"))
            next.remove("custom_fields")
        }
        return if (tooltype == "tracking") trackingConfig(next, entriesData) else next
    }

    /**
     * @param configAtV35 The config of the entry's tool, before [config] rewrote it
     * @return [entry] of a [tooltype] tool, as it stands at v36
     */
    fun entry(tooltype: String, entry: Entry, configAtV35: JSONObject): Entry {
        val rewritten = when (tooltype) {
            "notes" -> notes(entry)
            "messages" -> messages(entry)
            "tracking" -> trackingEntry(entry, configAtV35)
            else -> entry
        }
        return rewritten.copy(
            // An empty object says nothing a missing one does not
            extra = rewritten.extra?.takeIf { it.length() > 0 },
            state = rewritten.state?.takeIf { it.length() > 0 }
        )
    }

    /**
     * A note's position is state. Its name, "Note" on every note (the only value the schema
     * accepted), said nothing: notes have no name from v36.
     */
    private fun notes(entry: Entry): Entry =
        moveToState(entry, listOf("position")).copy(name = null)

    /** An occurrence's status, delivery, read, archived and origin are state. */
    private fun messages(entry: Entry): Entry =
        moveToState(entry, listOf("status", "notification_sent", "read", "archived", "triggered_by"))

    /**
     * A tracking config's settings move to the shape the field types give them:
     * - the main field's settings go under "value": a scale's bounds and labels, a choice's
     *   options, a yes/no's labels (those of its first shortcut that has any: the labels belong
     *   to the field, one pair per tool), a text's length (the former limit, a page), a timer's
     *   precision (the second, what it counted in);
     * - a numeric tool declares its units, those of its shortcuts then those its entries use;
     * - a shortcut keeps its name, and for a numeric or counter tool the value it enters
     *   (default_quantity, default_increment), and for a numeric one its unit.
     */
    private fun trackingConfig(config: JSONObject, entriesData: List<JSONObject>): JSONObject {
        val type = config.getString("type")
        val items = config.optJSONArray("items") ?: JSONArray()
        val value = JSONObject()
        val shortcuts = JSONArray()

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val shortcut = JSONObject().put("name", item.getString("name"))
            when (type) {
                "numeric" -> {
                    if (item.has("default_quantity") && item.get("default_quantity") is Number) shortcut.put("value", item.get("default_quantity"))
                    item.optString("unit").takeIf { it.isNotBlank() }?.let { shortcut.put("unit", it) }
                }
                "counter" -> if (item.has("default_increment")) shortcut.put("value", item.get("default_increment"))
                "boolean" -> if (!value.has("true_label")) {
                    item.optString("true_label").takeIf { it.isNotBlank() }?.let { value.put("true_label", it) }
                    item.optString("false_label").takeIf { it.isNotBlank() }?.let { value.put("false_label", it) }
                }
            }
            shortcuts.put(shortcut)
        }

        when (type) {
            "numeric" -> {
                val units = LinkedHashSet<String>()
                for (i in 0 until shortcuts.length()) shortcuts.getJSONObject(i).optString("unit").takeIf { it.isNotBlank() }?.let { units.add(it) }
                entriesData.forEach { data -> data.optString("unit").takeIf { it.isNotBlank() }?.let { units.add(it) } }
                if (units.isNotEmpty()) config.put("units", JSONArray(units.toList()))
            }
            "scale" -> {
                // The bounds the reads fell back to when the config had none
                value.put("min", if (config.has("min")) config.get("min") else 1)
                value.put("max", if (config.has("max")) config.get("max") else 10)
                config.optString("min_label").takeIf { it.isNotBlank() }?.let { value.put("min_label", it) }
                config.optString("max_label").takeIf { it.isNotBlank() }?.let { value.put("max_label", it) }
            }
            "choice" -> value.put("options", config.optJSONArray("options") ?: JSONArray())
            "text" -> value.put("length", "LONG")
            "timer" -> value.put("precision", "SECOND")
        }

        listOf("min", "max", "min_label", "max_label", "options", "unit", "true_label", "false_label").forEach { config.remove(it) }
        config.put("items", shortcuts)
        if (value.length() > 0) config.put("value", value)
        return config
    }

    /**
     * A tracking entry keeps its value alone, under "value", and a numeric one its unit. What
     * it copied from the config (a scale's bounds and labels, a yes/no's labels, a choice's
     * options) and its display text ("raw") are read from the config and the field type now.
     * A timer's seconds become milliseconds.
     *
     * @throws IllegalArgumentException for a tracking type this does not know
     */
    private fun trackingEntry(entry: Entry, config: JSONObject): Entry {
        val data = entry.data
        val type = data.optString("type").ifEmpty { config.optString("type") }
        val next = JSONObject()
        fun copy(from: String) { if (data.has(from) && !data.isNull(from)) next.put("value", data.get(from)) }

        when (type) {
            "numeric" -> {
                copy("quantity")
                data.optString("unit").takeIf { it.isNotBlank() }?.let { next.put("unit", it) }
            }
            "scale" -> copy("rating")
            "boolean" -> copy("state")
            "choice" -> copy("selected_option")
            "counter" -> copy("increment")
            "text" -> copy("text")
            "timer" -> if (data.has("duration_seconds")) next.put("value", data.getLong("duration_seconds") * 1000L)
            else -> throw IllegalArgumentException("Unknown tracking type: $type")
        }
        return entry.copy(data = next)
    }

    /** [entry] with [keys] moved from its data to its state, where present. */
    private fun moveToState(entry: Entry, keys: List<String>): Entry {
        val data = JSONObject(entry.data.toString())
        val state = JSONObject(entry.state?.toString() ?: "{}")
        keys.filter { data.has(it) }.forEach { key ->
            state.put(key, data.get(key))
            data.remove(key)
        }
        return entry.copy(data = data, state = state)
    }

    /**
     * Rewrites a backup's tool instances and entries, in place. The backup's entries still carry
     * "custom_fields"; they leave with "extra" and "state".
     */
    fun backup(data: JSONObject) {
        val configsAtV35 = mutableMapOf<String, JSONObject>()
        val entriesData = mutableMapOf<String, MutableList<JSONObject>>()
        data.optJSONArray("tool_data")?.forEachObject { row ->
            entriesData.getOrPut(row.getString("tool_instance_id")) { mutableListOf() }.add(JSONObject(row.getString("data")))
        }

        data.optJSONArray("tool_instances")?.forEachObject { instance ->
            val id = instance.getString("id")
            val tooltype = instance.getString("tooltype")
            val config = JSONObject(instance.getString("config_json"))
            configsAtV35[id] = config
            instance.put("config_json", config(tooltype, config, entriesData[id] ?: emptyList()).toString())
        }

        data.optJSONArray("tool_data")?.forEachObject { row ->
            val toolId = row.getString("tool_instance_id")
            val tooltype = row.getString("tooltype")
            val rewritten = entry(
                tooltype,
                Entry(
                    name = row.optString("name").takeIf { row.has("name") && !row.isNull("name") },
                    data = JSONObject(row.getString("data")),
                    extra = row.optString("custom_fields").takeIf { row.has("custom_fields") && !row.isNull("custom_fields") }?.let { JSONObject(it) },
                    state = null
                ),
                // Entries belong to a tool of the same backup (the foreign key deletes the
                // others): one without is a damaged backup, and the import stops before wiping.
                configsAtV35[toolId] ?: throw IllegalStateException("Entry ${row.optString("id")} belongs to tool $toolId, absent from the backup")
            )
            row.remove("custom_fields")
            if (rewritten.name != null) row.put("name", rewritten.name) else row.remove("name")
            row.put("data", rewritten.data.toString())
            rewritten.extra?.let { row.put("extra", it.toString()) }
            rewritten.state?.let { row.put("state", it.toString()) }
        }
    }

    private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) action(getJSONObject(i))
    }
}
