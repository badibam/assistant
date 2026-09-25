package com.assistant.core.ai.prompts

import com.assistant.core.fields.FieldValueSchema
import com.assistant.core.utils.DateTimeConverter
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * The values of an entry in the form the model speaks, and back.
 *
 * The app stores instants and durations as milliseconds; the model reads and writes them in
 * ISO 8601 (2025-03-15T14:30:00+01:00, PT1H25M). SchemaModelView turns the schema; this turns
 * the values the schema describes, so both read the same marks (FieldValueSchema.EPOCH_MILLIS
 * and DURATION_MILLIS) and cannot disagree on where a date or a duration sits.
 *
 * The schema says where to convert, never the look of a value: a TEXT field holding
 * "2026-09-25T10:00:00" or "PT1H" stays text. A property the schema does not describe is left
 * as it is, and the validation downstream decides what it is worth.
 */
object ModelValues {

    /**
     * [value] with every stored instant and duration the [schema] marks written in ISO 8601.
     * A marked value that is not a number is left as it is: there is nothing stored to convert.
     */
    fun toModel(value: Any?, schema: JSONObject, zone: ZoneId): Any? =
        walk(value, schema) { format, leaf ->
            if (leaf !is Number) return@walk leaf
            when (format) {
                FieldValueSchema.EPOCH_MILLIS -> DateTimeConverter.timestampToISO(leaf.toLong(), zone)
                FieldValueSchema.DURATION_MILLIS -> Duration.ofMillis(leaf.toLong()).toString()
                else -> leaf
            }
        }

    /**
     * [value] with every ISO 8601 instant and duration the model wrote, where the [schema]
     * marks one, turned into milliseconds. A number there is kept, being already the stored form.
     *
     * @throws IllegalArgumentException on a marked string that does not read, naming it: left
     *   as text, it would only be refused later as a string where a number was expected
     */
    fun fromModel(value: Any?, schema: JSONObject, zone: ZoneId): Any? =
        walk(value, schema) { format, leaf ->
            if (leaf !is String) return@walk leaf
            when (format) {
                FieldValueSchema.EPOCH_MILLIS -> DateTimeConverter.isoToTimestamp(leaf, zone)
                FieldValueSchema.DURATION_MILLIS -> parseDuration(leaf)
                else -> leaf
            }
        }

    /**
     * Rebuild [value] along [schema]: objects by their properties, lists by their items, and a
     * leaf through [convert] with the format its schema carries.
     */
    private fun walk(value: Any?, schema: JSONObject, convert: (String, Any) -> Any): Any? {
        return when (value) {
            null, JSONObject.NULL -> value
            is JSONObject -> {
                val properties = schema.optJSONObject("properties")
                val result = JSONObject()
                value.keys().forEach { key ->
                    val child = properties?.optJSONObject(key)
                    val item = value.get(key)
                    result.put(key, if (child != null) walk(item, child, convert) else item)
                }
                result
            }
            is Map<*, *> -> {
                val properties = schema.optJSONObject("properties")
                value.entries.associate { (key, item) ->
                    val child = properties?.optJSONObject(key.toString())
                    key.toString() to (if (child != null) walk(item, child, convert) else item)
                }
            }
            is JSONArray -> {
                val items = schema.optJSONObject("items")
                val result = JSONArray()
                for (i in 0 until value.length()) {
                    val item = value.get(i)
                    result.put(if (items != null) walk(item, items, convert) else item)
                }
                result
            }
            is List<*> -> {
                val items = schema.optJSONObject("items")
                value.map { if (items != null) walk(it, items, convert) else it }
            }
            else -> {
                val format = schema.optString("format")
                if (format.isEmpty()) value else convert(format, value)
            }
        }
    }

    /** An ISO 8601 duration in milliseconds. Days are read as 24 hours, as java.time does. */
    private fun parseDuration(iso: String): Long =
        try {
            Duration.parse(iso).toMillis()
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid ISO 8601 duration: $iso", e)
        }
}
