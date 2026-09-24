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
            "app_config_ai_limits" -> createAILimitsSchema(context)
            // Future schema types:
            // "app_config_ui" -> createUiSchema(context)
            // "app_config_data" -> createDataSchema(context)
            else -> null
        }
    }

    override fun getAllSchemaIds(): List<String> {
        return listOf("app_config_format", "app_config_ai_limits")
    }

    override fun getFormFieldName(fieldName: String, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (fieldName) {
            "week_start_day" -> s.shared("app_config_format_week_start_day")
            "day_start_hour" -> s.shared("app_config_format_day_start_hour")
            "locale_override" -> s.shared("app_config_format_locale_override")
            "timezone_override" -> s.shared("app_config_format_timezone_override")
            "use_24_hour_format" -> s.shared("app_config_format_use_24_hour_format")
            "date_format_pattern" -> s.shared("app_config_format_date_format_pattern")
            "time_separator" -> s.shared("app_config_format_time_separator")
            "relative_label_limits" -> s.shared("app_config_format_relative_label_limits")
            "hour_limit" -> s.shared("app_config_format_hour_limit")
            "day_limit" -> s.shared("app_config_format_day_limit")
            "week_limit" -> s.shared("app_config_format_week_limit")
            "month_limit" -> s.shared("app_config_format_month_limit")
            "year_limit" -> s.shared("app_config_format_year_limit")
            "chat_max_autonomous_roundtrips" -> s.shared("app_config_ai_limits_chat")
            "automation_max_autonomous_roundtrips" -> s.shared("app_config_ai_limits_automation")
            "chat_max_data_chars" -> s.shared("app_config_ai_data_chat")
            "automation_max_data_chars" -> s.shared("app_config_ai_data_automation")
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
                "timezone_override": {
                    "type": ["string", "null"],
                    "description": "${s.shared("app_config_schema_format_timezone_override")}"
                },
                "use_24_hour_format": {
                    "type": ["boolean", "null"],
                    "description": "${s.shared("app_config_schema_format_use_24_hour_format")}"
                },
                "date_format_pattern": {
                    "type": ["string", "null"],
                    "description": "${s.shared("app_config_schema_format_date_format_pattern")}"
                },
                "time_separator": {
                    "type": "string",
                    "enum": [":", "h"],
                    "description": "${s.shared("app_config_schema_format_time_separator")}"
                },
                "relative_label_limits": {
                    "type": "object",
                    "description": "${s.shared("app_config_schema_format_relative_label_limits")}",
                    "properties": {
                        "hour_limit": {
                            "type": "integer",
                            "minimum": ${HOUR_LIMIT_RANGE.first},
                            "maximum": ${HOUR_LIMIT_RANGE.last},
                            "description": "${s.shared("app_config_schema_format_hour_limit")}"
                        },
                        "day_limit": {
                            "type": "integer",
                            "minimum": ${DAY_LIMIT_RANGE.first},
                            "maximum": ${DAY_LIMIT_RANGE.last},
                            "description": "${s.shared("app_config_schema_format_day_limit")}"
                        },
                        "week_limit": {
                            "type": "integer",
                            "minimum": ${WEEK_LIMIT_RANGE.first},
                            "maximum": ${WEEK_LIMIT_RANGE.last},
                            "description": "${s.shared("app_config_schema_format_week_limit")}"
                        },
                        "month_limit": {
                            "type": "integer",
                            "minimum": ${MONTH_LIMIT_RANGE.first},
                            "maximum": ${MONTH_LIMIT_RANGE.last},
                            "description": "${s.shared("app_config_schema_format_month_limit")}"
                        },
                        "year_limit": {
                            "type": "integer",
                            "minimum": ${YEAR_LIMIT_RANGE.first},
                            "maximum": ${YEAR_LIMIT_RANGE.last},
                            "description": "${s.shared("app_config_schema_format_year_limit")}"
                        }
                    },
                    "required": ["hour_limit", "day_limit", "week_limit", "month_limit", "year_limit"],
                    "additionalProperties": false
                }
            },
            "required": ["week_start_day", "day_start_hour", "time_separator", "relative_label_limits"],
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

    /**
     * The two AI roundtrip limits. Each call is paid for, and the limit is what stops an AI that
     * keeps calling itself: the bounds keep it at least one call and below a runaway.
     */
    private fun createAILimitsSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)

        val content = """
        {
            "type": "object",
            "properties": {
                "chat_max_autonomous_roundtrips": {
                    "type": "integer",
                    "minimum": ${AI_LIMITS_CHAT_RANGE.first},
                    "maximum": ${AI_LIMITS_CHAT_RANGE.last},
                    "description": "${s.shared("app_config_schema_ai_limits_chat")}"
                },
                "automation_max_autonomous_roundtrips": {
                    "type": "integer",
                    "minimum": ${AI_LIMITS_AUTOMATION_RANGE.first},
                    "maximum": ${AI_LIMITS_AUTOMATION_RANGE.last},
                    "description": "${s.shared("app_config_schema_ai_limits_automation")}"
                },
                "chat_max_data_chars": {
                    "type": "integer",
                    "minimum": ${AI_DATA_CHAT_RANGE.first},
                    "maximum": ${AI_DATA_CHAT_RANGE.last},
                    "description": "${s.shared("app_config_schema_ai_data_chat")}"
                },
                "automation_max_data_chars": {
                    "type": "integer",
                    "minimum": ${AI_DATA_AUTOMATION_RANGE.first},
                    "maximum": ${AI_DATA_AUTOMATION_RANGE.last},
                    "description": "${s.shared("app_config_schema_ai_data_automation")}"
                }
            },
            "required": ["chat_max_autonomous_roundtrips", "automation_max_autonomous_roundtrips", "chat_max_data_chars", "automation_max_data_chars"],
            "additionalProperties": false
        }
        """.trimIndent()

        return Schema(
            id = "app_config_ai_limits",
            displayName = s.shared("settings_ai_limits"),
            description = s.shared("settings_ai_limits_description"),
            category = SchemaCategory.APP_CONFIG,
            content = content
        )
    }

    /** The bounds of the relative label limits, shared by the schema and the sliders of the format screen */
    val HOUR_LIMIT_RANGE = 1..24
    val DAY_LIMIT_RANGE = 1..30
    val WEEK_LIMIT_RANGE = 1..12
    val MONTH_LIMIT_RANGE = 1..24
    val YEAR_LIMIT_RANGE = 1..10

    /** The bounds of the AI limits, shared by the schema and the sliders of the settings screen */
    val AI_LIMITS_CHAT_RANGE = 1..50
    val AI_LIMITS_AUTOMATION_RANGE = 1..100

    /** The data size thresholds, in characters, and the step of their sliders */
    val AI_DATA_CHAT_RANGE = IntProgression.fromClosedRange(5_000, 100_000, 5_000)
    val AI_DATA_AUTOMATION_RANGE = IntProgression.fromClosedRange(10_000, 500_000, 10_000)
}
