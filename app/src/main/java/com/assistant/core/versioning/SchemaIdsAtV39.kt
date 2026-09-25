package com.assistant.core.versioning

import org.json.JSONObject

/**
 * Brings a tool config to its v39 form: without "schema_id" and "data_schema_id". Both named the
 * schemas of the config and of its entries, which are now generated from the tool type's
 * declarations: copies of what the tool type and the config already say. Shared by the database
 * migration and the backup import.
 */
object SchemaIdsAtV39 {

    fun config(config: JSONObject): JSONObject =
        JSONObject(config.toString()).apply {
            remove("schema_id")
            remove("data_schema_id")
        }

    /** Rewrites the backup document's tool configs in place. */
    fun backup(data: JSONObject) {
        val instances = data.optJSONArray("tool_instances") ?: return
        for (i in 0 until instances.length()) {
            val instance = instances.getJSONObject(i)
            instance.put("config_json", config(JSONObject(instance.getString("config_json"))).toString())
        }
    }
}
