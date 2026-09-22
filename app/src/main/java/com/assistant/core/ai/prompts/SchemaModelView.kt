package com.assistant.core.ai.prompts

import org.json.JSONArray
import org.json.JSONObject

/**
 * The view of a schema handed to the model.
 *
 * The app stores an instant as milliseconds and speaks ISO 8601 to the model
 * (docs/design/date-boundary.md), so the two ends have different forms on purpose. A schema
 * describes the form at its own end: the stored one says a number, and this one says the string
 * the model actually receives and is expected to send back.
 *
 * Nothing is maintained twice. The stored schema marks an instant with `"format":
 * "epoch-millis"` -- BaseSchemas for timestamp, created_at and updated_at, and
 * CustomFieldsSchemaGenerator for a DATETIME field -- and this view is computed from that mark.
 * A property without it is left alone, so a plain numeric field stays a number.
 */
object SchemaModelView {

    private const val EPOCH_MILLIS = "epoch-millis"

    /**
     * Rewrite every instant in a schema as the ISO 8601 string the model reads.
     *
     * Walks the whole document rather than a known list of places: a marked property can sit
     * anywhere -- at the root of a data schema, under custom_fields, inside a nested object.
     */
    fun forModel(schema: JSONObject): JSONObject {
        val result = JSONObject()

        schema.keys().forEach { key ->
            result.put(key, convertValue(schema.get(key)))
        }

        return if (isInstant(result)) asIsoString(result) else result
    }

    private fun convertValue(value: Any): Any = when (value) {
        is JSONObject -> forModel(value)
        is JSONArray -> convertArray(value)
        else -> value
    }

    private fun convertArray(array: JSONArray): JSONArray {
        val result = JSONArray()
        for (i in 0 until array.length()) {
            result.put(convertValue(array.get(i)))
        }
        return result
    }

    private fun isInstant(node: JSONObject): Boolean =
        node.optString("format") == EPOCH_MILLIS

    /**
     * Replace the numeric constraints with the string ones, and keep everything else the property
     * said -- its description, and whether the system manages it.
     */
    private fun asIsoString(node: JSONObject): JSONObject {
        val result = JSONObject()

        node.keys().forEach { key ->
            if (key !in setOf("type", "format", "minimum", "maximum")) {
                result.put(key, node.get(key))
            }
        }

        result.put("type", "string")
        result.put("format", "date-time")

        return result
    }
}
