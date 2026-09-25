package com.assistant.core.versioning

import org.json.JSONArray
import org.json.JSONObject

/**
 * Brings the CHOICE fields of a tool config to their v37 form: each option a group that holds
 * its value and what describes it ({"value": "work", "color": "BLUE"}), instead of a string
 * beside tables indexed by the options ("option_colors", "option_labels").
 *
 * A config holds CHOICE definitions in two places: its user's fields ("extra_fields"), and the
 * value settings of a tracking tool of type choice ("value"). Entries are untouched: they store
 * the option's value, which does not change. The single place that says so, shared by the
 * database migration and the backup import.
 */
object ChoiceOptionsAtV37 {

    /** [config] of a [tooltype] tool, as it stands at v37. */
    fun config(tooltype: String, config: JSONObject): JSONObject {
        val next = JSONObject(config.toString())
        next.optJSONArray("extra_fields")?.let { fields ->
            for (i in 0 until fields.length()) {
                val field = fields.getJSONObject(i)
                if (field.optString("type") == "CHOICE") field.optJSONObject("config")?.let { options(it) }
            }
        }
        if (tooltype == "tracking" && next.optString("type") == "choice") {
            next.optJSONObject("value")?.let { options(it) }
        }
        return next
    }

    /** Rewrites the backup document's tool configs in place. */
    fun backup(data: JSONObject) {
        val instances = data.optJSONArray("tool_instances") ?: return
        for (i in 0 until instances.length()) {
            val instance = instances.getJSONObject(i)
            val config = JSONObject(instance.getString("config_json"))
            instance.put("config_json", config(instance.getString("tooltype"), config).toString())
        }
    }

    /** One CHOICE config's options as groups, its tables folded into them. */
    private fun options(choice: JSONObject) {
        val stored = choice.optJSONArray("options") ?: JSONArray()
        val colors = choice.optJSONObject("option_colors")
        val labels = choice.optJSONObject("option_labels")
        val groups = JSONArray()
        for (i in 0 until stored.length()) {
            val option = stored.get(i)
            // Already a group: a config written after the change, left as it is
            if (option is JSONObject) {
                groups.put(option)
                continue
            }
            val value = option.toString()
            groups.put(JSONObject().put("value", value).also { group ->
                labels?.optString(value)?.takeIf { it.isNotEmpty() }?.let { group.put("label", it) }
                colors?.optString(value)?.takeIf { it.isNotEmpty() }?.let { group.put("color", it) }
            })
        }
        choice.put("options", groups)
        choice.remove("option_colors")
        choice.remove("option_labels")
    }
}
