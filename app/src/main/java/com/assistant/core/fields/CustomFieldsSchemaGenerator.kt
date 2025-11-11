package com.assistant.core.fields

import com.assistant.core.validation.FieldLimits
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow

/**
 * Enriches JSON schemas with custom field definitions.
 *
 * Takes a base schema and a tool instance config, extracts custom_fields definitions,
 * and adds them as properties to the schema under a "custom_fields" object.
 *
 * This is a critical component for validation - all custom field validation
 * goes through the enriched schema via SchemaValidator.
 *
 * Architecture:
 * - Generates JSON Schema for all field types based on their config
 * - Extensible with when(type) for each field type
 */
object CustomFieldsSchemaGenerator {

    /**
     * Enriches a schema with custom fields from tool instance config.
     *
     * @param baseSchemaJson The base schema JSON string (already merged from base + specific)
     * @param configJson The tool instance config JSON string containing custom_fields array
     * @return Enriched schema JSON string with custom_fields properties added
     */
    fun enrichSchema(baseSchemaJson: String, configJson: String): String {
        val schemaObj = JSONObject(baseSchemaJson)
        val configObj = JSONObject(configJson)

        // Extract custom_fields array from config
        val customFieldsArray = configObj.optJSONArray("custom_fields")
        if (customFieldsArray == null || customFieldsArray.length() == 0) {
            // No custom fields defined, return schema as-is
            return schemaObj.toString()
        }

        // Parse field definitions
        val fieldDefinitions = try {
            customFieldsArray.toFieldDefinitions()
        } catch (e: Exception) {
            // If parsing fails, return schema without enrichment
            // This should never happen if config validation is correct
            return schemaObj.toString()
        }

        // Get or create the root properties object
        val properties = schemaObj.optJSONObject("properties") ?: JSONObject().also {
            schemaObj.put("properties", it)
        }

        // Create custom_fields schema object
        val customFieldsSchema = createCustomFieldsSchema(fieldDefinitions)

        // Add custom_fields property to schema
        properties.put("custom_fields", customFieldsSchema)

        return schemaObj.toString()
    }

    /**
     * Creates the JSON schema for the custom_fields object.
     *
     * Structure:
     * {
     *   "type": "object",
     *   "properties": {
     *     "field_name_1": { "type": "string", "description": "..." },
     *     "field_name_2": { "type": "number", "minimum": 0 }
     *   },
     *   "additionalProperties": false
     * }
     */
    private fun createCustomFieldsSchema(fieldDefinitions: List<FieldDefinition>): JSONObject {
        val schema = JSONObject()
        schema.put("type", "object")

        val properties = JSONObject()
        for (fieldDef in fieldDefinitions) {
            val fieldSchema = createFieldSchema(fieldDef)
            properties.put(fieldDef.name, fieldSchema)
        }

        schema.put("properties", properties)

        // Allow only defined properties (no additional properties)
        schema.put("additionalProperties", false)

        // Note: No "required" array - all custom fields are optional

        return schema
    }

    /**
     * Creates the JSON schema for a single field based on its type.
     * Generates validation schemas for all supported field types.
     */
    private fun createFieldSchema(fieldDef: FieldDefinition): JSONObject {
        return when (fieldDef.type) {
            FieldType.TEXT_SHORT -> {
                JSONObject().apply {
                    put("type", "string")
                    put("maxLength", FieldLimits.SHORT_LENGTH)
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.TEXT_LONG -> {
                JSONObject().apply {
                    put("type", "string")
                    put("maxLength", FieldLimits.LONG_LENGTH)
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.TEXT_UNLIMITED -> {
                JSONObject().apply {
                    put("type", "string")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.NUMERIC -> {
                JSONObject().apply {
                    put("type", "number")

                    fieldDef.config?.let { config ->
                        (config["min"] as? Number)?.let { put("minimum", it) }
                        (config["max"] as? Number)?.let { put("maximum", it) }

                        // multipleOf for decimals validation
                        val decimals = (config["decimals"] as? Number)?.toInt() ?: 0
                        if (decimals > 0) {
                            val multipleOf = 10.0.pow(-decimals.toDouble())
                            put("multipleOf", multipleOf)
                        }
                    }

                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.SCALE -> {
                JSONObject().apply {
                    put("type", "number")

                    fieldDef.config?.let { config ->
                        (config["min"] as? Number)?.let { put("minimum", it) }
                        (config["max"] as? Number)?.let { put("maximum", it) }

                        // multipleOf for step validation
                        val step = (config["step"] as? Number)?.toDouble() ?: 1.0
                        if (step > 0) {
                            put("multipleOf", step)
                        }
                    }

                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.CHOICE -> {
                val multiple = (fieldDef.config?.get("multiple") as? Boolean) ?: false

                if (multiple) {
                    // Multiple choice: array of strings with enum validation
                    JSONObject().apply {
                        put("type", "array")

                        val itemSchema = JSONObject().apply {
                            put("type", "string")
                            fieldDef.config?.let { config ->
                                (config["options"] as? List<*>)?.let { options ->
                                    val enumArray = JSONArray(options)
                                    put("enum", enumArray)
                                }
                            }
                        }
                        put("items", itemSchema)
                        put("uniqueItems", true)

                        if (fieldDef.description != null) {
                            put("description", fieldDef.description)
                        }
                    }
                } else {
                    // Single choice: string with enum validation
                    JSONObject().apply {
                        put("type", "string")

                        fieldDef.config?.let { config ->
                            (config["options"] as? List<*>)?.let { options ->
                                val enumArray = JSONArray(options)
                                put("enum", enumArray)
                            }
                        }

                        if (fieldDef.description != null) {
                            put("description", fieldDef.description)
                        }
                    }
                }
            }

            FieldType.BOOLEAN -> {
                JSONObject().apply {
                    put("type", "boolean")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.RANGE -> {
                JSONObject().apply {
                    put("type", "object")

                    val startSchema = JSONObject().apply {
                        put("type", "number")
                        fieldDef.config?.let { config ->
                            (config["min"] as? Number)?.let { put("minimum", it) }
                            (config["max"] as? Number)?.let { put("maximum", it) }
                        }
                    }

                    val endSchema = JSONObject().apply {
                        put("type", "number")
                        fieldDef.config?.let { config ->
                            (config["min"] as? Number)?.let { put("minimum", it) }
                            (config["max"] as? Number)?.let { put("maximum", it) }
                        }
                    }

                    val properties = JSONObject().apply {
                        put("start", startSchema)
                        put("end", endSchema)
                    }
                    put("properties", properties)

                    val required = JSONArray().apply {
                        put("start")
                        put("end")
                    }
                    put("required", required)

                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.DATE -> {
                JSONObject().apply {
                    put("type", "string")
                    put("format", "date")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.TIME -> {
                JSONObject().apply {
                    put("type", "string")
                    put("pattern", "^([01]?[0-9]|2[0-3]):[0-5][0-9]$")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.DATETIME -> {
                JSONObject().apply {
                    put("type", "string")
                    put("format", "date-time")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }
        }
    }
}
