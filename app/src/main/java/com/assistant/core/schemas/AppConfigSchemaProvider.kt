package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.strings.Strings

/**
 * Schema provider for App Configuration categories
 * Used for validation of format, UI, and data configuration settings
 */
object AppConfigSchemaProvider : SchemaProvider {

    override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
        return when (schemaId) {
            "app_config_format" -> createFormatSchema(context)
            // Future schema types:
            // "app_config_ui" -> createUiSchema(context)
            // "app_config_data" -> createDataSchema(context)
            else -> null
        }
    }

    override fun getAllSchemaIds(): List<String> {
        return listOf("app_config_format")
        // return listOf("app_config_format", "app_config_ui", "app_config_data")
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (fieldName) {
            "week_start_day" -> s.shared("app_config_format_week_start_day")
            "day_start_hour" -> s.shared("app_config_format_day_start_hour")
            "locale_override" -> s.shared("app_config_format_locale_override")
            "relative_label_limits" -> s.shared("app_config_format_relative_label_limits")
            "hour_limit" -> s.shared("app_config_format_hour_limit")
            "day_limit" -> s.shared("app_config_format_day_limit")
            "week_limit" -> s.shared("app_config_format_week_limit")
            "month_limit" -> s.shared("app_config_format_month_limit")
            "year_limit" -> s.shared("app_config_format_year_limit")
            else -> fieldName
        }
    }

    private fun createFormatSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)

        val content = """
        {
            "type": "object",
            "properties": {
                "week_start_day": {
                    "type": "string",
                    "enum": ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"],
                    "description": "${s.shared("app_config_schema_format_week_start_day")}"
                },
                "day_start_hour": {
                    "type": "integer",
                    "minimum": 0,
                    "maximum": 23,
                    "description": "${s.shared("app_config_schema_format_day_start_hour")}"
                },
                "locale_override": {
                    "type": ["string", "null"],
                    "description": "${s.shared("app_config_schema_format_locale_override")}"
                },
                "relative_label_limits": {
                    "type": "object",
                    "description": "${s.shared("app_config_schema_format_relative_label_limits")}",
                    "properties": {
                        "hour_limit": {
                            "type": "integer",
                            "minimum": 1,
                            "maximum": 24,
                            "description": "${s.shared("app_config_schema_format_hour_limit")}"
                        },
                        "day_limit": {
                            "type": "integer",
                            "minimum": 1,
                            "maximum": 30,
                            "description": "${s.shared("app_config_schema_format_day_limit")}"
                        },
                        "week_limit": {
                            "type": "integer",
                            "minimum": 1,
                            "maximum": 12,
                            "description": "${s.shared("app_config_schema_format_week_limit")}"
                        },
                        "month_limit": {
                            "type": "integer",
                            "minimum": 1,
                            "maximum": 24,
                            "description": "${s.shared("app_config_schema_format_month_limit")}"
                        },
                        "year_limit": {
                            "type": "integer",
                            "minimum": 1,
                            "maximum": 10,
                            "description": "${s.shared("app_config_schema_format_year_limit")}"
                        }
                    },
                    "required": ["hour_limit", "day_limit", "week_limit", "month_limit", "year_limit"],
                    "additionalProperties": false
                }
            },
            "required": ["week_start_day", "day_start_hour", "relative_label_limits"],
            "additionalProperties": false
        }
        """.trimIndent()

        return Schema(
            id = "app_config_format",
            displayName = s.shared("app_config_format_schema_display_name"),
            description = s.shared("app_config_format_schema_description"),
            category = SchemaCategory.APP_CONFIG,
            content = content
        )
    }

}