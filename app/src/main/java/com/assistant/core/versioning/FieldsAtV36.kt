package com.assistant.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings tool configs and entries to their v36 form, the one field system.
 *
 * - A config's "custom_fields" (the user's field definitions) becomes "extra_fields".
 * - An entry's "custom_fields" column (the user's values) becomes "extra", and an entry gains a
 *   "state" object: what the app and its actions produce on it, moved out of "data".
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

    /** @return [config] of a [tooltype] tool, as it stands at v36 */
    fun config(tooltype: String, config: JSONObject): JSONObject {
        val next = JSONObject(config.toString())
        if (next.has("custom_fields")) {
            next.put("extra_fields", next.get("custom_fields"))
            next.remove("custom_fields")
        }
        return next
    }

    /**
     * @param configAtV35 The config of the entry's tool, before [config] rewrote it
     * @return [entry] of a [tooltype] tool, as it stands at v36
     */
    fun entry(tooltype: String, entry: Entry, configAtV35: JSONObject): Entry =
        entry.copy(
            // An empty object says nothing a missing one does not
            extra = entry.extra?.takeIf { it.length() > 0 },
            state = entry.state?.takeIf { it.length() > 0 }
        )

    /**
     * Rewrites a backup's tool instances and entries, in place. The backup's entries still carry
     * "custom_fields"; they leave with "extra" and "state".
     */
    fun backup(data: JSONObject) {
        val configsAtV35 = mutableMapOf<String, JSONObject>()

        data.optJSONArray("tool_instances")?.forEachObject { instance ->
            val tooltype = instance.getString("tooltype")
            val config = JSONObject(instance.getString("config_json"))
            configsAtV35[instance.getString("id")] = config
            instance.put("config_json", config(tooltype, config).toString())
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
