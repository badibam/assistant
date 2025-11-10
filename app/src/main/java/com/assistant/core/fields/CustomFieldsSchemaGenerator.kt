package com.assistant.core.fields

import org.json.JSONArray
import org.json.JSONObject

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
 * - V1: Only TEXT_UNLIMITED type, simple string schema
 * - Future: Extensible with when(type) for complex types (SCALE, CHOICE, etc.)
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
     *     "field_name_2": { "type": "string" }
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
     *
     * V1: Only TEXT_UNLIMITED (simple string)
     * Future: Add complex schemas for other types (SCALE with min/max, CHOICE with enum, etc.)
     */
    private fun createFieldSchema(fieldDef: FieldDefinition): JSONObject {
        return when (fieldDef.type) {
            FieldType.TEXT_UNLIMITED -> {
                JSONObject().apply {
                    put("type", "string")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }
            // Future types will be added here with when branches
            // Example for future NUMERIC type:
            // FieldType.NUMERIC -> {
            //     JSONObject().apply {
            //         put("type", "number")
            //         fieldDef.config?.let { config ->
            //             config["min"]?.let { put("minimum", it) }
            //             config["max"]?.let { put("maximum", it) }
            //         }
            //         if (fieldDef.description != null) {
            //             put("description", fieldDef.description)
            //         }
            //     }
            // }
        }
    }
}
