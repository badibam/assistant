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
 * @property config Type-specific configuration (null for TEXT_UNLIMITED, required for types like SCALE)
 */
data class FieldDefinition(
    val name: String,
    val displayName: String,
    val description: String?,
    val type: FieldType,
    val alwaysVisible: Boolean,
    val config: Map<String, Any>?
)

/**
 * Converts this FieldDefinition to a JSONObject for storage/transmission.
 */
fun FieldDefinition.toJson(): JSONObject {
    return JSONObject().apply {
        put("name", name)
        put("display_name", displayName)
        if (description != null) {
            put("description", description)
        }
        put("type", type.name)
        put("always_visible", alwaysVisible)
        if (config != null) {
            put("config", JSONObject(config))
        }
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
            name = getString("name"),
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
            config = optJSONObject("config")?.let { configJson ->
                // Convert JSONObject to Map<String, Any>
                val map = mutableMapOf<String, Any>()
                configJson.keys().forEach { key ->
                    map[key] = configJson.get(key)
                }
                map
            }
        )
    } catch (e: ValidationException) {
        throw e
    } catch (e: Exception) {
        throw ValidationException("Failed to parse custom field: ${e.message}", e)
    }
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
        FieldType.TEXT_SHORT, FieldType.TEXT_LONG, FieldType.TEXT_UNLIMITED -> {
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
    }
}

/**
 * Format NUMERIC value with decimals and unit
 */
private fun formatNumericValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    val number = (value as? Number)?.toDouble() ?: return s.shared("label_no_value")

    val decimals = (config?.get("decimals") as? Number)?.toInt() ?: 0
    val unit = config?.get("unit") as? String

    // Format number, removing trailing zeros
    val formatted = if (decimals > 0) {
        String.format("%.${decimals}f", number).trimEnd('0').trimEnd('.')
    } else {
        number.toInt().toString()
    }

    val unitSuffix = unit?.let { " $it" } ?: ""
    return "$formatted$unitSuffix"
}

/**
 * Format SCALE value with range and labels
 * Format: "value (min à max - "min_label" à "max_label")"
 */
private fun formatScaleValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    val number = (value as? Number) ?: return s.shared("label_no_value")

    val min = (config?.get("min") as? Number) ?: 0
    val max = (config?.get("max") as? Number) ?: 10

    val minLabelKey = config?.get("min_label") as? String
    val maxLabelKey = config?.get("max_label") as? String

    val labelsStr = if (minLabelKey != null && maxLabelKey != null) {
        val minLabel = s.shared(minLabelKey)
        val maxLabel = s.shared(maxLabelKey)
        " - \"$minLabel\" à \"$maxLabel\""
    } else {
        ""
    }

    return "$number ($min à $max$labelsStr)"
}

/**
 * Format CHOICE value (single or multiple)
 */
private fun formatChoiceValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    val multiple = (config?.get("multiple") as? Boolean) ?: false

    return if (multiple) {
        val list = value as? List<*>
        if (list.isNullOrEmpty()) {
            s.shared("label_no_value")
        } else {
            list.joinToString(", ")
        }
    } else {
        val str = value as? String
        if (str.isNullOrEmpty()) s.shared("label_no_value") else str
    }
}

/**
 * Format BOOLEAN value with translated labels
 */
private fun formatBooleanValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    return when (value) {
        true -> {
            val trueLabel = config?.get("true_label") as? String ?: "label_yes"
            s.shared(trueLabel)
        }
        false -> {
            val falseLabel = config?.get("false_label") as? String ?: "label_no"
            s.shared(falseLabel)
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

    // Format numbers, removing trailing zeros
    val formattedStart = if (decimals > 0) {
        String.format("%.${decimals}f", start).trimEnd('0').trimEnd('.')
    } else {
        start.toInt().toString()
    }

    val formattedEnd = if (decimals > 0) {
        String.format("%.${decimals}f", end).trimEnd('0').trimEnd('.')
    } else {
        end.toInt().toString()
    }

    val unitSuffix = unit?.let { " $it" } ?: ""
    return "$formattedStart - $formattedEnd$unitSuffix"
}

/**
 * Format DATE value (ISO 8601 YYYY-MM-DD → short format dd/MM/yyyy)
 * Uses DateUtils for consistent formatting with rest of app
 */
private fun formatDateValue(value: Any?, s: StringsContext): String {
    val dateStr = value as? String ?: return s.shared("no_value")

    return try {
        // Parse ISO 8601 → timestamp → format using DateUtils
        val timestamp = com.assistant.core.utils.DateUtils.parseIso8601Date(dateStr)
        com.assistant.core.utils.DateUtils.formatDateForDisplay(timestamp)
    } catch (e: Exception) {
        dateStr // Fallback to raw string if parsing fails
    }
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
            com.assistant.core.utils.DateUtils.formatTimeForDisplay(timestamp)
        }
    } catch (e: Exception) {
        timeStr // Fallback to raw string if parsing fails
    }
}

/**
 * Format DATETIME value (ISO 8601 YYYY-MM-DDTHH:MM:SS → short format dd/MM/yyyy HH:MM)
 * Uses DateUtils for consistent formatting with rest of app
 */
private fun formatDateTimeValue(value: Any?, config: Map<String, Any>?, s: StringsContext): String {
    val dateTimeStr = value as? String ?: return s.shared("no_value")

    return try {
        // Parse ISO 8601 → timestamp
        val timestamp = com.assistant.core.utils.DateUtils.parseIso8601DateTime(dateTimeStr)

        val timeFormat = config?.get("time_format") as? String ?: "24h"

        if (timeFormat == "12h") {
            // Custom format for 12h mode (DateUtils only does 24h)
            val parts = dateTimeStr.split("T")
            if (parts.size == 2) {
                // Format date part using DateUtils
                val dateTimestamp = com.assistant.core.utils.DateUtils.parseIso8601Date(parts[0])
                val formattedDate = com.assistant.core.utils.DateUtils.formatDateForDisplay(dateTimestamp)

                // Format time part as 12h
                val timeStr = parts[1].substringBefore(":") + ":" + parts[1].split(":").getOrNull(1)
                val timeParts = timeStr.split(":")
                if (timeParts.size == 2) {
                    val hour24 = timeParts[0].toInt()
                    val minute = timeParts[1]

                    val period = if (hour24 < 12) "AM" else "PM"
                    val hour12 = when {
                        hour24 == 0 -> 12
                        hour24 <= 12 -> hour24
                        else -> hour24 - 12
                    }

                    "$formattedDate $hour12:$minute $period"
                } else {
                    dateTimeStr
                }
            } else {
                dateTimeStr
            }
        } else {
            // 24h format - use DateUtils
            com.assistant.core.utils.DateUtils.formatFullDateTime(timestamp)
        }
    } catch (e: Exception) {
        dateTimeStr // Fallback to raw string if parsing fails
    }
}

/**
 * Exception thrown when field definition validation fails.
 */
class ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
