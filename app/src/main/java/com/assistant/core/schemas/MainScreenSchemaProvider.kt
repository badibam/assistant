package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.strings.Strings

/**
 * Schema provider for MainScreen UI configuration
 */
object MainScreenSchemaProvider : SchemaProvider {

    override fun getSchema(schemaId: String, context: Context): Schema? {
        return when (schemaId) {
            "main_screen_config" -> createMainScreenConfigSchema(context)
            else -> null
        }
    }

    override fun getAllSchemaIds(): List<String> {
        return listOf("main_screen_config")
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(context = context)
        return when(fieldName) {
            "zone_display_mode" -> s.shared("main_screen_zone_display_mode")
            "show_zone_descriptions" -> s.shared("main_screen_show_zone_descriptions")
            "zones_per_row" -> s.shared("main_screen_zones_per_row")
            "enable_quick_actions" -> s.shared("main_screen_enable_quick_actions")
            else -> s.shared("label_field_parameter")
        }
    }

    private fun createMainScreenConfigSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)

        val content = """
        {
            "type": "object",
            "properties": {
                "zone_display_mode": {
                    "type": "string",
                    "enum": ["list", "grid", "compact"],
                    "default": "list",
                    "description": "${s.shared("main_screen_schema_zone_display_mode")}"
                },
                "show_zone_descriptions": {
                    "type": "boolean",
                    "default": true,
                    "description": "${s.shared("main_screen_schema_show_zone_descriptions")}"
                },
                "zones_per_row": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 4,
                    "default": 2,
                    "description": "${s.shared("main_screen_schema_zones_per_row")}"
                },
                "enable_quick_actions": {
                    "type": "boolean",
                    "default": true,
                    "description": "${s.shared("main_screen_schema_enable_quick_actions")}"
                }
            },
            "required": [],
            "additionalProperties": false
        }
        """.trimIndent()

        return Schema(
            id = "main_screen_config",
            displayName = s.shared("main_screen_config_schema_display_name"),
            description = s.shared("main_screen_config_schema_description"),
            category = SchemaCategory.APP_CONFIG,
            content = content
        )
    }

}