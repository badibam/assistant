package com.assistant.core.fields

import android.content.Context
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.strings.Strings

/**
 * Schema provider for custom field type definitions.
 *
 * Provides JSON Schema definitions for each available field type,
 * allowing the AI to query field type specifications and constraints.
 *
 * Schema ID format: "field_type_{FIELD_TYPE_NAME}"
 * Example: "field_type_TEXT_UNLIMITED"
 *
 * Architecture:
 * - Single source of truth: FieldType enum
 * - Dynamic schema generation based on available field types
 * - Extensible pattern for adding new field types
 */
object FieldTypeSchemaProvider : SchemaProvider {

    /**
     * Get schema for a specific field type
     *
     * @param schemaId Schema identifier (e.g., "field_type_TEXT_UNLIMITED")
     * @param context Android context for internationalization
     * @param toolInstanceId Not used for field type schemas (always null)
     * @return Schema object or null if schemaId doesn't match field type pattern
     */
    override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
        // Extract field type name from schema ID
        if (!schemaId.startsWith("field_type_")) return null

        val fieldTypeName = schemaId.removePrefix("field_type_")

        // Try to match with FieldType enum
        val fieldType = try {
            FieldType.valueOf(fieldTypeName)
        } catch (e: IllegalArgumentException) {
            return null  // Unknown field type
        }

        return when (fieldType) {
            FieldType.TEXT_UNLIMITED -> createTextUnlimitedSchema(context)
        }
    }

    /**
     * Get all available field type schema IDs
     *
     * Dynamically generated from FieldType enum to ensure single source of truth
     *
     * @return List of all field type schema IDs (e.g., ["field_type_TEXT_UNLIMITED"])
     */
    override fun getAllSchemaIds(): List<String> {
        return FieldType.entries.map { fieldType ->
            "field_type_${fieldType.name}"
        }
    }

    /**
     * Get user-friendly field name for display in validation errors
     *
     * @param fieldName Technical field name
     * @param context Android context for string resources
     * @return User-friendly field name
     */
    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(context = context)
        return when(fieldName) {
            "name" -> s.shared("label_name")
            "display_name" -> s.shared("label_display_name")
            "type" -> s.shared("label_type")
            "always_visible" -> s.shared("label_always_visible")
            "default_value" -> s.shared("label_default_value")
            "config" -> s.shared("label_config")
            else -> s.shared("label_field_generic")
        }
    }

    // ================================================================
    // Custom Fields Items Schema (for BaseSchemas integration)
    // ================================================================

    /**
     * Get the JSON Schema for custom_fields array items.
     *
     * This is the single source of truth for custom field validation.
     * Returns a oneOf schema that validates against any supported field type.
     *
     * Used by BaseSchemas to generate the custom_fields validation schema.
     *
     * @param context Android context for internationalization
     * @return JSON Schema string for custom_fields items with oneOf all field types
     */
    fun getCustomFieldsItemsSchema(context: Context): String {
        val s = Strings.`for`(context = context)

        // Build oneOf array with all field type schemas
        val fieldTypeSchemas = FieldType.entries.joinToString(",\n") { fieldType ->
            when (fieldType) {
                FieldType.TEXT_UNLIMITED -> createTextUnlimitedItemSchema(context)
            }
        }

        return """
        {
            "oneOf": [
                $fieldTypeSchemas
            ]
        }
        """.trimIndent()
    }

    // ================================================================
    // Field Type Schema Builders
    // ================================================================

    /**
     * Build TEXT_UNLIMITED JSON Schema (single source of truth)
     *
     * This is the only place where the TEXT_UNLIMITED schema is defined.
     * Used by both AI queries and BaseSchemas validation.
     */
    private fun buildTextUnlimitedSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)

        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-zA-Z0-9_]+$",
                    "minLength": 1,
                    "maxLength": 60,
                    "description": "${s.shared("field_type_schema_name_description")}"
                },
                "display_name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 60,
                    "description": "${s.shared("field_type_schema_display_name_description")}"
                },
                "type": {
                    "type": "string",
                    "const": "TEXT_UNLIMITED",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "default_value": {
                    "type": "string",
                    "description": "${s.shared("field_type_schema_default_value_description")}"
                }
            },
            "required": ["name", "display_name", "type"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    /**
     * Schema for TEXT_UNLIMITED field type (wrapped for AI queries)
     *
     * Wraps the JSON schema in a Schema object with metadata.
     */
    private fun createTextUnlimitedSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)

        return Schema(
            id = "field_type_TEXT_UNLIMITED",
            displayName = s.shared("field_type_text_unlimited_display_name"),
            description = s.shared("field_type_text_unlimited_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildTextUnlimitedSchemaJson(context)
        )
    }

    /**
     * Schema for TEXT_UNLIMITED field type (raw JSON for BaseSchemas)
     *
     * Returns the raw JSON schema for use in oneOf arrays.
     */
    private fun createTextUnlimitedItemSchema(context: Context): String {
        return buildTextUnlimitedSchemaJson(context)
    }
}
