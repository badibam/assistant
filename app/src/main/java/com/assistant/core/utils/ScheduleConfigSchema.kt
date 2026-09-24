package com.assistant.core.utils

import android.content.Context
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory

/**
 * JSON Schema for ScheduleConfig validation
 *
 * Provides complete validation for all 6 schedule patterns:
 * - DailyMultiple: Multiple times per day
 * - WeeklySimple: Specific days with single time
 * - MonthlyRecurrent: Specific months with fixed day
 * - WeeklyCustom: Different times for different days
 * - YearlyRecurrent: Same dates every year
 * - SpecificDates: One-shot executions
 *
 * Usage: Can be used directly or referenced via $ref in other schemas
 */
object ScheduleConfigSchema {

    const val SCHEMA_ID = "schedule_config"

    /**
     * Returns the complete JSON Schema for ScheduleConfig validation
     *
     * Note: All patterns are inlined (no definitions) for easy reusability in other schemas
     * The schema validates all 6 schedule pattern types with their specific structures
     */
    fun getSchema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = SCHEMA_ID,
            displayName = s.shared("schedule_config_display_name"),
            description = s.shared("schedule_config_description"),
            category = SchemaCategory.UTILITY,
            content = content { key -> s.shared(key) }
        )
    }

    /**
     * The schema's JSON, [describe] giving the text of each description key. Apart from
     * Context so the agreement between this schema and ScheduleConfig can be checked on the JVM.
     */
    fun content(describe: (String) -> String): String {
        return """
        {
            "${'$'}schema": "http://json-schema.org/draft-07/schema#",
            "${'$'}id": "$SCHEMA_ID",
            "type": ["object", "null"],
            "description": "${describe("schedule_config_description")}",

            "properties": {
                "pattern": {
                    "oneOf": [
                        {
                            "type": "object",
                            "properties": {
                                "type": {
                                    "type": "string",
                                    "const": "DailyMultiple"
                                },
                                "times": {
                                    "type": "array",
                                    "items": {
                                        "type": "string",
                                        "pattern": "^([01]\\d|2[0-3]):([0-5]\\d)${'$'}"
                                    },
                                    "minItems": 1,
                                    "description": "${describe("schedule_daily_times")}"
                                }
                            },
                            "required": ["type", "times"]
                        },
                        {
                            "type": "object",
                            "properties": {
                                "type": {
                                    "type": "string",
                                    "const": "WeeklySimple"
                                },
                                "days_of_week": {
                                    "type": "array",
                                    "items": {
                                        "type": "integer",
                                        "minimum": 1,
                                        "maximum": 7
                                    },
                                    "minItems": 1,
                                    "description": "${describe("schedule_days_of_week")}"
                                },
                                "time": {
                                    "type": "string",
                                    "pattern": "^([01]\\d|2[0-3]):([0-5]\\d)${'$'}",
                                    "description": "${describe("schedule_time_format")}"
                                }
                            },
                            "required": ["type", "days_of_week", "time"]
                        },
                        {
                            "type": "object",
                            "properties": {
                                "type": {
                                    "type": "string",
                                    "const": "MonthlyRecurrent"
                                },
                                "months": {
                                    "type": "array",
                                    "items": {
                                        "type": "integer",
                                        "minimum": 1,
                                        "maximum": 12
                                    },
                                    "minItems": 1,
                                    "description": "${describe("schedule_months")}"
                                },
                                "day_of_month": {
                                    "type": "integer",
                                    "minimum": 1,
                                    "maximum": 31,
                                    "description": "${describe("schedule_day_of_month")}"
                                },
                                "time": {
                                    "type": "string",
                                    "pattern": "^([01]\\d|2[0-3]):([0-5]\\d)${'$'}",
                                    "description": "${describe("schedule_time_format")}"
                                }
                            },
                            "required": ["type", "months", "day_of_month", "time"]
                        },
                        {
                            "type": "object",
                            "properties": {
                                "type": {
                                    "type": "string",
                                    "const": "WeeklyCustom"
                                },
                                "moments": {
                                    "type": "array",
                                    "items": {
                                        "type": "object",
                                        "properties": {
                                            "day_of_week": {
                                                "type": "integer",
                                                "minimum": 1,
                                                "maximum": 7,
                                                "description": "${describe("schedule_day_of_week")}"
                                            },
                                            "time": {
                                                "type": "string",
                                                "pattern": "^([01]\\d|2[0-3]):([0-5]\\d)${'$'}",
                                                "description": "${describe("schedule_time_format")}"
                                            }
                                        },
                                        "required": ["day_of_week", "time"]
                                    },
                                    "minItems": 1,
                                    "description": "${describe("schedule_weekly_moments")}"
                                }
                            },
                            "required": ["type", "moments"]
                        },
                        {
                            "type": "object",
                            "properties": {
                                "type": {
                                    "type": "string",
                                    "const": "YearlyRecurrent"
                                },
                                "dates": {
                                    "type": "array",
                                    "items": {
                                        "type": "object",
                                        "properties": {
                                            "month": {
                                                "type": "integer",
                                                "minimum": 1,
                                                "maximum": 12,
                                                "description": "${describe("schedule_month")}"
                                            },
                                            "day": {
                                                "type": "integer",
                                                "minimum": 1,
                                                "maximum": 31,
                                                "description": "${describe("schedule_day")}"
                                            },
                                            "time": {
                                                "type": "string",
                                                "pattern": "^([01]\\d|2[0-3]):([0-5]\\d)${'$'}",
                                                "description": "${describe("schedule_time_format")}"
                                            }
                                        },
                                        "required": ["month", "day", "time"]
                                    },
                                    "minItems": 1,
                                    "description": "${describe("schedule_yearly_dates")}"
                                }
                            },
                            "required": ["type", "dates"]
                        },
                        {
                            "type": "object",
                            "properties": {
                                "type": {
                                    "type": "string",
                                    "const": "SpecificDates"
                                },
                                "timestamps": {
                                    "type": "array",
                                    "items": {
                                        "type": "integer",
                                        "minimum": 0
                                    },
                                    "minItems": 1,
                                    "description": "${describe("schedule_timestamps")}"
                                }
                            },
                            "required": ["type", "timestamps"]
                        }
                    ],
                    "description": "${describe("schedule_pattern")}"
                },
                "start_date": {
                    "type": ["integer", "null"],
                    "minimum": 0,
                    "description": "${describe("schedule_start_date")}"
                },
                "end_date": {
                    "type": ["integer", "null"],
                    "minimum": 0,
                    "description": "${describe("schedule_end_date")}"
                }
            },
            "required": ["pattern"]
        }
        """.trimIndent()
    }
}
