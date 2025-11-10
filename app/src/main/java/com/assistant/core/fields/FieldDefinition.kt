package com.assistant.core.fields

import android.content.Context
import com.assistant.core.strings.Strings
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
 * This function provides consistent formatting across the entire UI:
 * - TEXT_UNLIMITED: Returns the text as-is
 * - Future types (SCALE, NUMERIC, CHOICE, etc.): Will add formatted display
 *
 * @param value The raw value to format (can be null)
 * @param context Android context for accessing string resources
 * @return Formatted string ready for display in UI
 */
fun FieldDefinition.formatValue(value: Any?, context: Context): String {
    val s = Strings.`for`(context = context)

    // Return "no value" for null/empty values
    if (value == null) return s.shared("no_value")

    return when (type) {
        FieldType.TEXT_UNLIMITED -> value.toString()

        // Future field types will be added here with specific formatting:
        // FieldType.SCALE -> formatScaleValue(value, config, s)
        // FieldType.NUMERIC -> formatNumericValue(value, config, s)
        // FieldType.CHOICE -> formatChoiceValue(value, config, s)
        // FieldType.BOOLEAN -> if (value as? Boolean == true) s.shared("yes") else s.shared("no")
        // etc.
    }
}

/**
 * Exception thrown when field definition validation fails.
 */
class ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
