package com.assistant.core.versioning

import org.json.JSONObject

/**
 * Removes the min/max bounds a DATE or DATETIME custom field used to carry in its config.
 *
 * Those bounds were written by the config editor and validated for their shape, and then nothing
 * ever applied them: CustomFieldsSchemaGenerator copies a field's bounds into the value schema
 * for NUMBER and RANGE, never for the date types, and FieldValueValidator answers a date value
 * with "valid" without looking. They constrained nothing, so they were dropped rather than made
 * to work.
 *
 * The keys have to leave the stored configs too: the config schema declares
 * `"additionalProperties": false`, so a leftover min or max would fail the validation of a config
 * that used to pass. This is the single place that says so, shared by the database migration and
 * the backup import, which otherwise drift apart.
 *
 * It is history, not a rule. A field type that genuinely bounds its values still declares min and
 * max, and they are still honoured.
 */
object DateFieldBounds {

    private val BOUNDED_TYPES_REMOVED = setOf("DATE", "DATETIME")

    /**
     * Strip the dead bounds from a tool config's custom_fields array.
     *
     * @param config the tool instance config
     * @return the number of bounds removed, the config being modified in place
     */
    fun strip(config: JSONObject): Int {
        val customFields = config.optJSONArray("custom_fields") ?: return 0
        var removed = 0

        for (i in 0 until customFields.length()) {
            val field = customFields.optJSONObject(i) ?: continue
            if (field.optString("type") !in BOUNDED_TYPES_REMOVED) continue

            val fieldConfig = field.optJSONObject("config") ?: continue
            for (bound in listOf("min", "max")) {
                if (fieldConfig.has(bound)) {
                    fieldConfig.remove(bound)
                    removed++
                }
            }

            // A config left empty says nothing, and the editor no longer writes one for DATE.
            if (fieldConfig.length() == 0) field.remove("config")
        }

        return removed
    }
}
