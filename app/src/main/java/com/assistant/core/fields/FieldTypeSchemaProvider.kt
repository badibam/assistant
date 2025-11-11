package com.assistant.core.fields

import android.content.Context
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.strings.Strings
import com.assistant.core.validation.FieldLimits

/**
 * Schema provider for custom field type definitions.
 *
 * Provides JSON Schema definitions for each available field type,
 * allowing the AI to query field type specifications and constraints.
 *
 * Schema ID format: "field_type_{FIELD_TYPE_NAME}"
 * Example: "field_type_TEXT_SHORT", "field_type_NUMERIC"
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
     * @param schemaId Schema identifier (e.g., "field_type_TEXT_SHORT")
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
            FieldType.TEXT_SHORT -> createTextShortSchema(context)
            FieldType.TEXT_LONG -> createTextLongSchema(context)
            FieldType.TEXT_UNLIMITED -> createTextUnlimitedSchema(context)
            FieldType.NUMERIC -> createNumericSchema(context)
            FieldType.SCALE -> createScaleSchema(context)
            FieldType.CHOICE -> createChoiceSchema(context)
            FieldType.BOOLEAN -> createBooleanSchema(context)
            FieldType.RANGE -> createRangeSchema(context)
            FieldType.DATE -> createDateSchema(context)
            FieldType.TIME -> createTimeSchema(context)
            FieldType.DATETIME -> createDateTimeSchema(context)
        }
    }

    /**
     * Get all available field type schema IDs
     *
     * Dynamically generated from FieldType enum to ensure single source of truth
     *
     * @return List of all field type schema IDs (e.g., ["field_type_TEXT_SHORT", ...])
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
        // Build oneOf array with all field type schemas
        val fieldTypeSchemas = FieldType.entries.joinToString(",\n") { fieldType ->
            when (fieldType) {
                FieldType.TEXT_SHORT -> buildTextShortSchemaJson(context)
                FieldType.TEXT_LONG -> buildTextLongSchemaJson(context)
                FieldType.TEXT_UNLIMITED -> buildTextUnlimitedSchemaJson(context)
                FieldType.NUMERIC -> buildNumericSchemaJson(context)
                FieldType.SCALE -> buildScaleSchemaJson(context)
                FieldType.CHOICE -> buildChoiceSchemaJson(context)
                FieldType.BOOLEAN -> buildBooleanSchemaJson(context)
                FieldType.RANGE -> buildRangeSchemaJson(context)
                FieldType.DATE -> buildDateSchemaJson(context)
                FieldType.TIME -> buildTimeSchemaJson(context)
                FieldType.DATETIME -> buildDateTimeSchemaJson(context)
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
     * Build TEXT_SHORT JSON Schema
     */
    private fun buildTextShortSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
                    "const": "TEXT_SHORT",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "default_value": {
                    "type": "string",
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.shared("field_type_schema_default_value_description")}"
                }
            },
            "required": ["name", "display_name", "type"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    private fun createTextShortSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "field_type_TEXT_SHORT",
            displayName = s.shared("field_type_text_short_display_name"),
            description = s.shared("field_type_text_short_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildTextShortSchemaJson(context)
        )
    }

    /**
     * Build TEXT_LONG JSON Schema
     */
    private fun buildTextLongSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
                    "const": "TEXT_LONG",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "default_value": {
                    "type": "string",
                    "maxLength": ${FieldLimits.LONG_LENGTH},
                    "description": "${s.shared("field_type_schema_default_value_description")}"
                }
            },
            "required": ["name", "display_name", "type"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    private fun createTextLongSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "field_type_TEXT_LONG",
            displayName = s.shared("field_type_text_long_display_name"),
            description = s.shared("field_type_text_long_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildTextLongSchemaJson(context)
        )
    }

    /**
     * Build TEXT_UNLIMITED JSON Schema (existing)
     */
    private fun buildTextUnlimitedSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
     * Build NUMERIC JSON Schema
     */
    private fun buildNumericSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
                    "const": "NUMERIC",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "config": {
                    "type": "object",
                    "properties": {
                        "unit": {
                            "type": "string",
                            "description": "Unité affichée (kg, cm, etc.)"
                        },
                        "min": {
                            "type": "number",
                            "description": "Valeur minimale"
                        },
                        "max": {
                            "type": "number",
                            "description": "Valeur maximale"
                        },
                        "decimals": {
                            "type": "integer",
                            "minimum": 0,
                            "default": 0,
                            "description": "Nombre de décimales"
                        },
                        "step": {
                            "type": "number",
                            "exclusiveMinimum": 0,
                            "description": "Incrément suggéré (UI uniquement)"
                        },
                        "default_value": {
                            "type": "number",
                            "description": "${s.shared("field_type_schema_default_value_description")}"
                        }
                    },
                    "additionalProperties": false
                }
            },
            "required": ["name", "display_name", "type"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    private fun createNumericSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "field_type_NUMERIC",
            displayName = s.shared("field_type_numeric_display_name"),
            description = s.shared("field_type_numeric_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildNumericSchemaJson(context)
        )
    }

    /**
     * Build SCALE JSON Schema
     */
    private fun buildScaleSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
                    "const": "SCALE",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "config": {
                    "type": "object",
                    "properties": {
                        "min": {
                            "type": "number",
                            "description": "Valeur minimale (obligatoire)"
                        },
                        "max": {
                            "type": "number",
                            "description": "Valeur maximale (obligatoire)"
                        },
                        "min_label": {
                            "type": "string",
                            "description": "Clé i18n pour label minimum (optionnel)"
                        },
                        "max_label": {
                            "type": "string",
                            "description": "Clé i18n pour label maximum (optionnel)"
                        },
                        "step": {
                            "type": "number",
                            "exclusiveMinimum": 0,
                            "default": 1,
                            "description": "Incrément"
                        },
                        "default_value": {
                            "type": "number",
                            "description": "${s.shared("field_type_schema_default_value_description")}"
                        }
                    },
                    "required": ["min", "max"],
                    "additionalProperties": false
                }
            },
            "required": ["name", "display_name", "type", "config"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    private fun createScaleSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "field_type_SCALE",
            displayName = s.shared("field_type_scale_display_name"),
            description = s.shared("field_type_scale_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildScaleSchemaJson(context)
        )
    }

    /**
     * Build CHOICE JSON Schema
     */
    private fun buildChoiceSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
                    "const": "CHOICE",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "config": {
                    "type": "object",
                    "properties": {
                        "options": {
                            "type": "array",
                            "items": {
                                "type": "string"
                            },
                            "minItems": 2,
                            "uniqueItems": true,
                            "description": "Liste d'options (minimum 2, pas de doublons)"
                        },
                        "multiple": {
                            "type": "boolean",
                            "default": false,
                            "description": "Autoriser sélection multiple"
                        },
                        "allow_custom": {
                            "type": "boolean",
                            "default": false,
                            "description": "Autoriser valeurs personnalisées (non implémenté en V1)"
                        },
                        "default_value": {
                            "oneOf": [
                                {
                                    "type": "string",
                                    "description": "Valeur par défaut (choix unique)"
                                },
                                {
                                    "type": "array",
                                    "items": {
                                        "type": "string"
                                    },
                                    "description": "Valeurs par défaut (choix multiple)"
                                }
                            ]
                        }
                    },
                    "required": ["options"],
                    "additionalProperties": false
                }
            },
            "required": ["name", "display_name", "type", "config"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    private fun createChoiceSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "field_type_CHOICE",
            displayName = s.shared("field_type_choice_display_name"),
            description = s.shared("field_type_choice_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildChoiceSchemaJson(context)
        )
    }

    /**
     * Build BOOLEAN JSON Schema
     */
    private fun buildBooleanSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
                    "const": "BOOLEAN",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "config": {
                    "type": "object",
                    "properties": {
                        "true_label": {
                            "type": "string",
                            "minLength": 1,
                            "default": "label_yes",
                            "description": "Clé i18n pour label vrai"
                        },
                        "false_label": {
                            "type": "string",
                            "minLength": 1,
                            "default": "label_no",
                            "description": "Clé i18n pour label faux"
                        },
                        "default_value": {
                            "type": "boolean",
                            "default": false,
                            "description": "${s.shared("field_type_schema_default_value_description")}"
                        }
                    },
                    "additionalProperties": false
                }
            },
            "required": ["name", "display_name", "type"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    private fun createBooleanSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "field_type_BOOLEAN",
            displayName = s.shared("field_type_boolean_display_name"),
            description = s.shared("field_type_boolean_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildBooleanSchemaJson(context)
        )
    }

    /**
     * Build RANGE JSON Schema
     */
    private fun buildRangeSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
                    "const": "RANGE",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "config": {
                    "type": "object",
                    "properties": {
                        "min": {
                            "type": "number",
                            "description": "Minimum absolu"
                        },
                        "max": {
                            "type": "number",
                            "description": "Maximum absolu"
                        },
                        "unit": {
                            "type": "string",
                            "description": "Unité affichée"
                        },
                        "decimals": {
                            "type": "integer",
                            "minimum": 0,
                            "default": 0,
                            "description": "Nombre de décimales"
                        },
                        "default_value": {
                            "type": "object",
                            "properties": {
                                "start": {
                                    "type": "number"
                                },
                                "end": {
                                    "type": "number"
                                }
                            },
                            "required": ["start", "end"],
                            "description": "${s.shared("field_type_schema_default_value_description")}"
                        }
                    },
                    "additionalProperties": false
                }
            },
            "required": ["name", "display_name", "type"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    private fun createRangeSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "field_type_RANGE",
            displayName = s.shared("field_type_range_display_name"),
            description = s.shared("field_type_range_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildRangeSchemaJson(context)
        )
    }

    /**
     * Build DATE JSON Schema
     */
    private fun buildDateSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
                    "const": "DATE",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "config": {
                    "type": "object",
                    "properties": {
                        "min": {
                            "type": "string",
                            "format": "date",
                            "description": "Date minimale (ISO 8601 YYYY-MM-DD)"
                        },
                        "max": {
                            "type": "string",
                            "format": "date",
                            "description": "Date maximale (ISO 8601 YYYY-MM-DD)"
                        },
                        "default_value": {
                            "type": "string",
                            "format": "date",
                            "description": "${s.shared("field_type_schema_default_value_description")}"
                        }
                    },
                    "additionalProperties": false
                }
            },
            "required": ["name", "display_name", "type"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    private fun createDateSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "field_type_DATE",
            displayName = s.shared("field_type_date_display_name"),
            description = s.shared("field_type_date_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildDateSchemaJson(context)
        )
    }

    /**
     * Build TIME JSON Schema
     */
    private fun buildTimeSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
                    "const": "TIME",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "config": {
                    "type": "object",
                    "properties": {
                        "format": {
                            "type": "string",
                            "enum": ["24h", "12h"],
                            "default": "24h",
                            "description": "Format d'affichage (stockage toujours 24h)"
                        },
                        "default_value": {
                            "type": "string",
                            "pattern": "^([01]?[0-9]|2[0-3]):[0-5][0-9]$",
                            "description": "${s.shared("field_type_schema_default_value_description")}"
                        }
                    },
                    "additionalProperties": false
                }
            },
            "required": ["name", "display_name", "type"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    private fun createTimeSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "field_type_TIME",
            displayName = s.shared("field_type_time_display_name"),
            description = s.shared("field_type_time_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildTimeSchemaJson(context)
        )
    }

    /**
     * Build DATETIME JSON Schema
     */
    private fun buildDateTimeSchemaJson(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "pattern": "^[a-z][a-z0-9_]*[a-z0-9]$|^[a-z]$",
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
                    "const": "DATETIME",
                    "description": "${s.shared("field_type_schema_type_description")}"
                },
                "always_visible": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("field_type_schema_always_visible_description")}"
                },
                "config": {
                    "type": "object",
                    "properties": {
                        "min": {
                            "type": "string",
                            "format": "date-time",
                            "description": "Date-heure minimale (ISO 8601 YYYY-MM-DDTHH:MM:SS)"
                        },
                        "max": {
                            "type": "string",
                            "format": "date-time",
                            "description": "Date-heure maximale (ISO 8601 YYYY-MM-DDTHH:MM:SS)"
                        },
                        "time_format": {
                            "type": "string",
                            "enum": ["24h", "12h"],
                            "default": "24h",
                            "description": "Format d'affichage de l'heure"
                        },
                        "default_value": {
                            "type": "string",
                            "format": "date-time",
                            "description": "${s.shared("field_type_schema_default_value_description")}"
                        }
                    },
                    "additionalProperties": false
                }
            },
            "required": ["name", "display_name", "type"],
            "additionalProperties": false
        }
        """.trimIndent()
    }

    private fun createDateTimeSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = "field_type_DATETIME",
            displayName = s.shared("field_type_datetime_display_name"),
            description = s.shared("field_type_datetime_description"),
            category = SchemaCategory.FIELD_TYPE,
            content = buildDateTimeSchemaJson(context)
        )
    }
}
