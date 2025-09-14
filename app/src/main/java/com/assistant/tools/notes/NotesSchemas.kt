package com.assistant.tools.notes

import android.content.Context
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.strings.Strings

/**
 * External JSON schemas for Notes tool type
 * Keeps schemas separate from business logic for better maintainability
 */
object NotesSchemas {

    /**
     * Configuration schema template with placeholders
     * Notes uses only the base configuration (no tool-specific config parameters)
     */
    private val CONFIG_SCHEMA_TEMPLATE = """
        {}
    """.trimIndent()

    /**
     * Data schema template with placeholders
     */
    private val DATA_SCHEMA_TEMPLATE = """
        {
            "properties": {
                "name": {
                    "type": "string",
                    "const": "Note",
                    "description": "{{DATA_NAME_DESC}}"
                },
                "timestamp": {
                    "type": "number",
                    "description": "{{DATA_TIMESTAMP_DESC}}"
                },
                "data": {
                    "type": "object",
                    "description": "{{DATA_DATA_DESC}}",
                    "properties": {
                        "content": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": ${BaseSchemas.FieldLimits.LONG_LENGTH},
                            "description": "{{DATA_CONTENT_DESC}}"
                        },
                        "position": {
                            "type": "integer",
                            "minimum": 0,
                            "description": "{{DATA_POSITION_DESC}}"
                        }
                    },
                    "required": ["content"],
                    "additionalProperties": false
                }
            },
            "required": ["name", "timestamp", "data"],
            "additionalProperties": false
        }
    """.trimIndent()

    /**
     * Get config schema with localized descriptions
     * Returns empty object as Notes has no tool-specific configuration
     */
    fun getConfigSchema(context: Context): String {
        return CONFIG_SCHEMA_TEMPLATE
    }

    /**
     * Get data schema with localized descriptions
     */
    fun getDataSchema(context: Context): String {
        val s = Strings.`for`(tool = "notes", context = context)
        return DATA_SCHEMA_TEMPLATE
            .replace("{{DATA_NAME_DESC}}", s.tool("schema_data_name"))
            .replace("{{DATA_TIMESTAMP_DESC}}", s.tool("schema_data_timestamp"))
            .replace("{{DATA_DATA_DESC}}", s.tool("schema_data_data"))
            .replace("{{DATA_CONTENT_DESC}}", s.tool("schema_data_content"))
            .replace("{{DATA_POSITION_DESC}}", s.tool("schema_data_position"))
    }
}