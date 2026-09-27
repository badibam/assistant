package com.assistant.core.fields

import android.content.Context
import com.assistant.core.strings.Strings
import com.assistant.core.strings.StringsContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Definition of a custom field that can be added to tool instances.
 *
 * Custom fields are user-defined fields that extend the data structure of tool entries.
 * Each field has a stable technical name (generated once, immutable) and a display name
 * that can be modified by the user.
 *
 * @property name Technical identifier in snake_case (generated once, immutable after creation)
 * @property displayName User-facing name (editable)
 * @property description Optional documentation for the field (visible in schema and config UI)
 * @property type Field type (immutable after creation)
 * @property alwaysVisible Whether to display the field in read mode even when empty
 * @property config Type-specific configuration (required for types like SCALE)
 * @property defaultValue A value of this field, in its stored form, suggested for a new entry:
 *   the form prefills it, a quick action applies it, the AI reads it in the schema. Never written
 *   by the service on its own: an absent value means "no answer" (docs/DATA.md)
 */
data class FieldDefinition(
    val name: String,
    val displayName: String,
    val description: String?,
    val type: FieldType,
    val alwaysVisible: Boolean,
    val config: Map<String, Any>?,
    val defaultValue: Any? = null
)

/**
 * Converts this FieldDefinition to a JSONObject for storage/transmission.
 */
fun FieldDefinition.toJson(): JSONObject {
    return JSONObject().apply {
        // A field that has not been named yet leaves the key out: the schema allows an absent
        // name, since the service assigns it, but an empty one fails its pattern and minLength.
        if (name.isNotEmpty()) {
            put("name", name)
        }
        put("display_name", displayName)
        if (description != null) {
            put("description", description)
        }
        put("type", type.name)
        put("always_visible", alwaysVisible)
        if (config != null) {
            put("config", JSONObject(config))
        }
        defaultValue?.let { put("default_value", JSONObject.wrap(it)) }
    }
}

/**
 * Parses a FieldDefinition from a JSONObject.
 *
 * @throws ValidationException if the field type is not supported or parsing fails
 */
fun JSONObject.toFieldDefinition(): FieldDefinition {
    return try {
        FieldDefinition(
            name = optString("name"),
            displayName = getString("display_name"),
            description = optString("description").takeIf { it.isNotEmpty() },
            type = try {
                FieldType.valueOf(getString("type"))
            } catch (e: IllegalArgumentException) {
                throw ValidationException(
                    "Custom field type '${getString("type")}' not supported. Please update the app."
                )
            },
            alwaysVisible = optBoolean("always_visible", false),
            config = optJSONObject("config")?.toFieldConfig(),
            defaultValue = com.assistant.core.utils.JsonUtils.toValue(opt("default_value")?.takeIf { it != JSONObject.NULL })
        )
    } catch (e: ValidationException) {
        throw e
    } catch (e: Exception) {
        throw ValidationException("Failed to parse custom field: ${e.message}", e)
    }
}

/**
 * Converts a field's config JSONObject to the Map form FieldDefinition holds, all the way down:
 * JSONArrays become Lists and JSONObjects become Maps, inside a list too (a CHOICE's options are
 * a list of groups).
 */
fun JSONObject.toFieldConfig(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key -> map[key] = toFieldConfigValue(get(key)) }
    return map
}

private fun toFieldConfigValue(value: Any): Any = when (value) {
    is JSONArray -> (0 until value.length()).map { toFieldConfigValue(value.get(it)) }
    is JSONObject -> value.toFieldConfig()
    else -> value
}

/**
 * Parses a list of FieldDefinitions from a JSONArray.
 */
fun JSONArray.toFieldDefinitions(): List<FieldDefinition> {
    val fields = mutableListOf<FieldDefinition>()
    for (i in 0 until length()) {
        fields.add(getJSONObject(i).toFieldDefinition())
    }
    return fields
}

/**
 * Converts a list of FieldDefinitions to a JSONArray.
 */
fun List<FieldDefinition>.toJsonArray(): JSONArray {
    return JSONArray().apply {
        forEach { field ->
            put(field.toJson())
        }
    }
}

/**
 * Formats a custom field value for display in the UI according to its type.
 *
 * This function provides consistent formatting across the entire UI for all field types.
 * Returns user-friendly, locale-aware formatted strings ready for display.
 *
 * @param value The raw value to format (can be null)
 * @param context Android context for accessing string resources
 * @return Formatted string ready for display in UI
 */
fun FieldDefinition.formatValue(value: Any?, context: Context): String {
    val s = Strings.`for`(context = context)

    // Return "no value" for null/empty values
    if (value == null) return s.shared("label_no_value")

    return when (type) {
        FieldType.TEXT -> {
            val text = value.toString()
            if (text.isEmpty()) s.shared("label_no_value") else text
        }

        FieldType.NUMERIC -> formatNumericValue(value, config, s)
        FieldType.SCALE -> formatScaleValue(value, config, s)
        FieldType.CHOICE -> formatChoiceValue(value, config, s)
        FieldType.BOOLEAN -> formatBooleanValue(value, config, s)
        FieldType.RANGE -> formatRangeValue(value, config, s)
        FieldType.DATE -> formatDateValue(value, s)
        FieldType.TIME -> formatTimeValue(value, config, s)
        FieldType.DATETIME -> formatDateTimeValue(value, config, s)
        FieldType.DURATION -> (value as? Number)?.let { Durations.format(it.toLong(), config, s) } ?: s.shared("label_no_value")
    }
}

/**
 * Format NUMERIC value with exactly its decimals (72 with 2 decimals is 72.00) and its unit
 */
private fun formatNumericValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    val number = (value as? Number)?.toDouble() ?: return s.shared("label_no_value")

    val decimals = (config?.get("decimals") as? Number)?.toInt() ?: 0
    val unit = config?.get("unit") as? String

    val formatted = NumericPrecision.format(number, decimals)

    val unitSuffix = unit?.let { " $it" } ?: ""
    return "$formatted$unitSuffix"
}

/**
 * Format SCALE value with range and labels
 * Format: "value (min to max - "min_label" to "max_label")", worded by the strings
 */
private fun formatScaleValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    val number = (value as? Number)?.toDouble() ?: return s.shared("label_no_value")

    val min = (config?.get("min") as? Number)?.toDouble() ?: 0.0
    val max = (config?.get("max") as? Number)?.toDouble() ?: 10.0
    val step = (config?.get("step") as? Number)?.toDouble() ?: 1.0

    // As many decimals as the scale's stops carry: 7 on a scale of whole numbers, never 7.0
    val decimals = com.assistant.core.ui.SliderSteps.decimals(min, step)
    fun shown(n: Double) = String.format(java.util.Locale.getDefault(), "%.${decimals}f", n)

    val minLabel = config?.get("min_label") as? String
    val maxLabel = config?.get("max_label") as? String

    return if (minLabel != null && maxLabel != null) {
        s.shared("field_scale_value_with_labels").format(shown(number), shown(min), shown(max), minLabel, maxLabel)
    } else {
        s.shared("field_scale_value").format(shown(number), shown(min), shown(max))
    }
}

/**
 * Format CHOICE value (one option, or a list of them in the order stored, which for a ranking
 * is the order chosen)
 */
private fun formatChoiceValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    val settings = ChoiceSettings.fromConfig(config)
    return if (settings.shape.isList) {
        val list = value as? List<*>
        if (list.isNullOrEmpty()) {
            s.shared("label_no_value")
        } else {
            list.joinToString(", ") { settings.labelOf(it.toString()) }
        }
    } else {
        val str = value as? String
        if (str.isNullOrEmpty()) s.shared("label_no_value") else settings.labelOf(str)
    }
}

/**
 * Format BOOLEAN value with user labels or default translated labels
 */
private fun formatBooleanValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    return when (value) {
        true -> {
            // Use user-provided label if exists, otherwise use default translated label
            config?.get("true_label") as? String ?: s.shared("label_yes")
        }
        false -> {
            // Use user-provided label if exists, otherwise use default translated label
            config?.get("false_label") as? String ?: s.shared("label_no")
        }
        else -> s.shared("label_no_value")
    }
}

/**
 * Format RANGE value with unit
 * Format: "start - end unit"
 */
private fun formatRangeValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    val rangeMap = value as? Map<*, *> ?: return s.shared("label_no_value")

    val start = (rangeMap["start"] as? Number)?.toDouble() ?: return s.shared("label_no_value")
    val end = (rangeMap["end"] as? Number)?.toDouble() ?: return s.shared("label_no_value")

    val decimals = (config?.get("decimals") as? Number)?.toInt() ?: 0
    val unit = config?.get("unit") as? String

    val formattedStart = NumericPrecision.format(start, decimals)
    val formattedEnd = NumericPrecision.format(end, decimals)

    val unitSuffix = unit?.let { " $it" } ?: ""
    return "$formattedStart - $formattedEnd$unitSuffix"
}

/**
 * Format DATE value (ISO 8601 YYYY-MM-DD → short format dd/MM/yyyy)
 * Uses DateUtils for consistent formatting with rest of app
 */
private fun formatDateValue(value: Any?, s: StringsContext): String {
    val dateStr = value as? String ?: return s.shared("no_value")

    // A value that is not a date is shown as it was stored, rather than as some other
    // date: the reader sees what is actually recorded.
    val timestamp = com.assistant.core.utils.DateUtils.parseIso8601Date(dateStr) ?: return dateStr
    return com.assistant.core.utils.DateUtils.formatDateForDisplay(timestamp)
}

/**
 * Format TIME value (HH:MM) according to display format
 * Uses DateUtils for consistent formatting with rest of app
 */
private fun formatTimeValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    val timeStr = value as? String ?: return s.shared("no_value")

    val format = config?.get("format") as? String ?: "24h"

    return try {
        if (format == "12h") {
            // Convert 24h to 12h format
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                val hour24 = parts[0].toInt()
                val minute = parts[1]

                val period = if (hour24 < 12) "AM" else "PM"
                val hour12 = when {
                    hour24 == 0 -> 12
                    hour24 <= 12 -> hour24
                    else -> hour24 - 12
                }

                "$hour12:$minute $period"
            } else {
                timeStr
            }
        } else {
            // 24h format - use DateUtils for consistency
            val timestamp = com.assistant.core.utils.DateUtils.parseIso8601Time(timeStr)
            if (timestamp == null) timeStr else com.assistant.core.utils.DateUtils.formatTimeForDisplay(timestamp)
        }
    } catch (e: Exception) {
        timeStr // Fallback to raw string if parsing fails
    }
}

/**
 * Format DATETIME value for display. The value is milliseconds, as the field's schema says.
 */
private fun formatDateTimeValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    val timestamp = (value as? Number)?.toLong() ?: return s.shared("no_value")

    val timeFormat = config?.get("time_format") as? String ?: "24h"
    if (timeFormat != "12h") {
        return com.assistant.core.utils.DateUtils.formatFullDateTime(timestamp)
    }

    // DateUtils only writes 24h, so the two halves are taken apart and the hour recast.
    val formattedDate = com.assistant.core.utils.DateUtils.formatDateForDisplay(timestamp)
    val timeParts = com.assistant.core.utils.DateUtils.formatTimeForDisplay(timestamp).split(":")
    if (timeParts.size != 2) {
        return com.assistant.core.utils.DateUtils.formatFullDateTime(timestamp)
    }

    val hour24 = timeParts[0].toInt()
    val minute = timeParts[1]
    val period = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 <= 12 -> hour24
        else -> hour24 - 12
    }

    return "$formattedDate $hour12:$minute $period"
}

/**
 * Exception thrown when field definition validation fails.
 */
class ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The default values of these fields, by name, those that have one: what a new entry starts
 * with when the one who creates it takes the suggestion (a form, a quick action).
 */
fun List<FieldDefinition>.defaultValues(): Map<String, Any?> =
    mapNotNull { field -> field.defaultValue?.let { field.name to it } }.toMap()

/**
 * The user's fields an update of an entry sends: the values [after] holds, and null for each
 * one [before] held that is now empty. The service keeps a key it is not sent and clears one sent
 * as null, so a value emptied in a form must be sent as null to be cleared.
 */
fun extraForUpdate(before: Map<String, Any?>, after: Map<String, Any?>): JSONObject {
    val kept = after.filterValues { it != null }
    return com.assistant.core.utils.JsonUtils.toJSONObject(kept + (before.keys - kept.keys).associateWith { null })
}
