package com.assistant.core.versioning

import org.json.JSONObject

/**
 * Brings a tool config to its v39 form, the one its declaration describes
 * (docs/design/config-fields.md):
 * - without "schema_id" and "data_schema_id": the schemas are generated from the tool type's
 *   declarations, and these were copies of what the tool type and the config already say;
 * - a Messages tool's delays as DURATIONs in milliseconds: "creation_horizon_days" becomes
 *   "creation_horizon", "validity_window_minutes" becomes "validity_window";
 * - without a null standing for "no setting": absent says it (a schedule's start or end date).
 *
 * Shared by the database migration and the backup import.
 */
object ToolConfigsAtV39 {

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val MILLIS_PER_MINUTE = 60_000L

    fun config(tooltype: String, config: JSONObject): JSONObject {
        val next = withoutNulls(config)
        next.remove("schema_id")
        next.remove("data_schema_id")
        if (tooltype == "messages") {
            if (next.has("creation_horizon_days")) next.put("creation_horizon", next.getLong("creation_horizon_days") * MILLIS_PER_DAY)
            if (next.has("validity_window_minutes")) next.put("validity_window", next.getLong("validity_window_minutes") * MILLIS_PER_MINUTE)
            next.remove("creation_horizon_days")
            next.remove("validity_window_minutes")
        }
        return next
    }

    /** Rewrites the backup document's tool configs in place. */
    fun backup(data: JSONObject) {
        val instances = data.optJSONArray("tool_instances") ?: return
        for (i in 0 until instances.length()) {
            val instance = instances.getJSONObject(i)
            instance.put("config_json", config(instance.getString("tooltype"), JSONObject(instance.getString("config_json"))).toString())
        }
    }

    /** [json] without its null values, at any depth of its objects. */
    private fun withoutNulls(json: JSONObject): JSONObject {
        val result = JSONObject()
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                JSONObject.NULL -> Unit
                is JSONObject -> result.put(key, withoutNulls(value))
                else -> result.put(key, value)
            }
        }
        return result
    }
}
