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
 * @throws ValidationException if a field definition cannot be read
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

        // Parse field definitions; an unreadable one throws, so the caller sees why
        val fieldDefinitions = customFieldsArray.toFieldDefinitions()

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
            val fieldSchema = valueSchema(fieldDef)
            properties.put(fieldDef.name, fieldSchema)
        }

        schema.put("properties", properties)

        // Allow only defined properties (no additional properties)
        schema.put("additionalProperties", false)

        // Note: No "required" array - all custom fields are optional

        return schema
    }

    /**
     * The JSON schema a value of [fieldDef] is held to, from its type and config.
     * The single place a field's value schema is written, whoever declared the field.
     */
    fun valueSchema(fieldDef: FieldDefinition): JSONObject {
        return when (fieldDef.type) {
            FieldType.TEXT -> {
                JSONObject().apply {
                    put("type", "string")

                    // Get length from config (default UNLIMITED)
                    val lengthStr = fieldDef.config?.get("length") as? String
                    val length = TextLength.fromString(lengthStr)

                    // Only set maxLength if not UNLIMITED
                    if (length != TextLength.UNLIMITED) {
                        put("maxLength", length.getLimit())
                    }

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

                        // The stops of the slider. multipleOf counts from 0, not from min: the
                        // config validation keeps min and max on the step so the two agree.
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
                // An open choice is held to its options too: the write that brings a new value
                // adds it to the options first, and the schema is built from that config.
                if (ChoiceSettings.fromConfig(fieldDef.config).shape.isList) {
                    // Multiple choice or ranking: array of distinct strings with enum validation.
                    // A ranking need not hold every option, so an option added later leaves the
                    // rankings already stored valid.
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
                    // Milliseconds, like every other instant the app stores. An ISO string
                    // written without an offset is wall-clock time: the same text would mean
                    // different moments in different timezones, and would sort wrong across a
                    // clock change. DATE and TIME stay strings, being a day and an hour rather
                    // than an instant.
                    put("type", "number")
                    put("minimum", 0)
                    // Marks the property as an instant, which is what lets the view handed to
                    // the model show it as ISO while the stored form stays a number. A plain
                    // NUMBER field carries no such mark and stays a number on both sides.
                    put("format", "epoch-millis")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }

            FieldType.DURATION -> {
                JSONObject().apply {
                    // Milliseconds, whatever the precision: the precision decides what the form
                    // offers and the screen shows, not what is stored, so a stopwatch can write
                    // the exact time it measured into a field entered in minutes.
                    put("type", "integer")
                    put("minimum", 0)
                    // Marks the property as a duration, which is what lets the view handed to
                    // the model show it in ISO 8601 (PT1H25M) while the stored form stays a number.
                    put("format", "duration-millis")
                    if (fieldDef.description != null) {
                        put("description", fieldDef.description)
                    }
                }
            }
        }
    }
}
