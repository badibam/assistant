package com.assistant.core.tools

/**
 * Base JSON Schemas for all ToolTypes
 * Provides common fields to reduce token usage in AI prompts
 * Specific ToolTypes extend these base schemas with their own fields
 */
object BaseSchemas {
    
    /**
     * Base configuration schema for all tool types
     * Common fields: name, description, management, display_mode, icon_name
     */
    fun getBaseConfigSchema(): String {
        return """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 60,
                    "description": "Display name for this tool instance"
                },
                "description": {
                    "type": "string",
                    "maxLength": 250,
                    "description": "Optional description for this tool instance"
                },
                "management": {
                    "type": "string",
                    "enum": ["manual", "ai"],
                    "description": "Management type: manual input or AI-assisted"
                },
                "display_mode": {
                    "type": "string",
                    "description": "Display mode for this tool instance"
                },
                "icon_name": {
                    "type": "string",
                    "maxLength": 60,
                    "default": "activity",
                    "description": "Icon name for this tool instance"
                }
            },
            "required": ["name", "management"]
        }
        """.trimIndent()
    }
    
    /**
     * Base data schema for all tool types
     * Common fields: id, tool_instance_id, zone_name, tool_instance_name, name, timestamps
     */
    fun getBaseDataSchema(): String {
        return """
        {
            "type": "object",
            "properties": {
                "id": {
                    "type": "string",
                    "description": "Unique identifier for this data entry"
                },
                "tool_instance_id": {
                    "type": "string",
                    "description": "ID of the tool instance this data belongs to"
                },
                "zone_name": {
                    "type": "string",
                    "maxLength": 60,
                    "description": "Name of the zone containing this tool"
                },
                "tool_instance_name": {
                    "type": "string", 
                    "maxLength": 60,
                    "description": "Name of the tool instance"
                },
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 60,
                    "description": "Name/identifier for this specific data entry"
                },
                "recorded_at": {
                    "type": "integer",
                    "minimum": 0,
                    "description": "Timestamp when this data was recorded (user-specified)"
                },
                "created_at": {
                    "type": "integer",
                    "minimum": 0,
                    "description": "Timestamp when this entry was created in the system"
                },
                "updated_at": {
                    "type": "integer",
                    "minimum": 0,
                    "description": "Timestamp when this entry was last updated"
                }
            },
            "required": ["tool_instance_id", "zone_name", "tool_instance_name", "name", "recorded_at"]
        }
        """.trimIndent()
    }
    
    /**
     * Utility function to merge base schema with specific schema
     * Returns an allOf schema that combines base and specific properties
     */
    fun createExtendedSchema(baseSchema: String, specificSchema: String): String {
        return """
        {
            "allOf": [
                $baseSchema,
                $specificSchema
            ]
        }
        """.trimIndent()
    }
    
    /**
     * Generic length constants for text fields across all tool types
     * Maps directly to FieldType text variants
     */
    object FieldLimits {
        const val SHORT_LENGTH = 60        // FieldType.TEXT - identifiers, names, labels
        const val MEDIUM_LENGTH = 250      // FieldType.TEXT_MEDIUM - descriptions, text values
        const val LONG_LENGTH = 1500       // FieldType.TEXT_LONG - long content
        const val UNLIMITED_LENGTH = Int.MAX_VALUE  // FieldType.TEXT_UNLIMITED - no limits
    }
}