package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.strings.Strings

/**
 * JSON schemas for application configuration categories validation
 * Each category has its own validation schema
 */
object AppConfigSchemas {
    
    /**
     * Schema for "format" category
     * Validation of application format parameters (temporal, locale, display)
     */
    fun getFormatSchema(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
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
    }
    
    // Future schemas:
    /*
    const val UI_SCHEMA = """
    {
        "type": "object", 
        "properties": {
            "default_display_mode": {
                "type": "string",
                "enum": ["icon", "minimal", "line", "condensed", "extended", "square", "full"]
            },
            "theme": {
                "type": "string", 
                "enum": ["default", "dark", "custom"]
            },
            "grid_columns": {
                "type": "integer",
                "minimum": 2,
                "maximum": 4
            }
        },
        "required": ["default_display_mode", "theme", "grid_columns"],
        "additionalProperties": false
    }
    """
    
    const val DATA_SCHEMA = """
    {
        "type": "object",
        "properties": {
            "default_history_limit": {
                "type": "integer",
                "enum": [10, 25, 100, 250, 1000]
            },
            "backup_frequency": {
                "type": "string",
                "enum": ["daily", "weekly", "manual"]
            },
            "data_retention_days": {
                "type": "integer",
                "minimum": 30
            }
        },
        "required": ["default_history_limit", "backup_frequency"],
        "additionalProperties": false
    }
    """
    */
    
    /**
     * Gets schema for a given category
     */
    fun getSchemaForCategory(category: String, context: Context): String? {
        return when (category) {
            "format" -> getFormatSchema(context)
            // "ui" -> getUiSchema(context)
            // "data" -> getDataSchema(context)
            else -> null
        }
    }
    
    /**
     * List of supported categories
     */
    fun getSupportedCategories(): List<String> {
        return listOf("format")
        // return listOf("format", "ui", "data")
    }
}