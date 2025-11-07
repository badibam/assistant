package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.FieldLimits
import com.assistant.core.strings.Strings

/**
 * Schema provider for Zone configuration (business entity)
 * Used by CreateZoneScreen and other zone-related forms
 */
object ZoneSchemaProvider : SchemaProvider {

    override fun getSchema(schemaId: String, context: Context): Schema? {
        return when (schemaId) {
            "zone_config" -> createZoneConfigSchema(context)
            else -> null
        }
    }

    override fun getAllSchemaIds(): List<String> {
        return listOf("zone_config")
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(context = context)
        return when(fieldName) {
            "name" -> s.shared("label_zone_name")
            "description" -> s.shared("label_description")
            "icon_name" -> s.shared("label_icon")
            "color" -> s.shared("label_color")
            "validateZoneConfigChanges" -> s.shared("label_validate_zone_config_changes")
            "validateToolConfigChanges" -> s.shared("label_validate_tool_config_changes")
            "validateToolDataChanges" -> s.shared("label_validate_tool_data_changes")
            "tool_groups" -> s.shared("label_tool_groups")
            "group" -> s.shared("label_group")
            else -> s.shared("label_field_generic")
        }
    }

    private fun createZoneConfigSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)

        val content = """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.shared("zone_schema_name")}"
                },
                "description": {
                    "type": "string",
                    "maxLength": ${FieldLimits.MEDIUM_LENGTH},
                    "description": "${s.shared("zone_schema_description")}"
                },
                "icon_name": {
                    "type": "string",
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "default": "folder",
                    "description": "${s.shared("zone_schema_icon_name")}"
                },
                "color": {
                    "type": "string",
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.shared("zone_schema_color")}"
                },
                "validateZoneConfigChanges": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("zone_schema_validate_zone_config_changes")}"
                },
                "validateToolConfigChanges": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("zone_schema_validate_tool_config_changes")}"
                },
                "validateToolDataChanges": {
                    "type": "boolean",
                    "default": false,
                    "description": "${s.shared("zone_schema_validate_tool_data_changes")}"
                },
                "tool_groups": {
                    "type": "array",
                    "items": {
                        "type": "string",
                        "minLength": 1,
                        "maxLength": ${FieldLimits.SHORT_LENGTH}
                    },
                    "uniqueItems": true,
                    "description": "${s.shared("zone_schema_tool_groups")}"
                },
                "group": {
                    "type": "string",
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.shared("zone_schema_group")}"
                }
            },
            "required": ["name"],
            "additionalProperties": false
        }
        """.trimIndent()

        return Schema(
            id = "zone_config",
            displayName = s.shared("zone_config_schema_display_name"),
            description = s.shared("zone_config_schema_description"),
            category = SchemaCategory.ZONE_CONFIG,
            content = content
        )
    }

}