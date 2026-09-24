package com.assistant.core.validation

import org.json.JSONObject

/**
 * The fields a data schema marks "system_managed": true, whose value the app produces and no
 * caller supplies. The mark is the rule: ToolDataService drops such a field from what a caller
 * sends inside "data", then produces it itself (tracking's raw, computed from the value).
 *
 * At the root of an entry the service reads named params only and derives the marked ones
 * itself -- tooltype from the tool, schema_id from its config, the timestamps from the clock --
 * so no caller's value reaches them either.
 */
object SystemManagedFields {

    /** The properties of "data" that [schemaContent] marks as system-managed. */
    fun inData(schemaContent: String): Set<String> {
        val dataProperties = JSONObject(schemaContent)
            .optJSONObject("properties")
            ?.optJSONObject("data")
            ?.optJSONObject("properties")
            ?: return emptySet()
        return dataProperties.keys().asSequence()
            .filter { dataProperties.optJSONObject(it)?.optBoolean("system_managed", false) == true }
            .toSet()
    }

    /** [data] as a caller sent it, without the fields [schemaContent] marks as system-managed. */
    fun dropFromData(data: JSONObject, schemaContent: String): JSONObject {
        val cleaned = JSONObject(data.toString())
        inData(schemaContent).forEach { cleaned.remove(it) }
        return cleaned
    }
}
