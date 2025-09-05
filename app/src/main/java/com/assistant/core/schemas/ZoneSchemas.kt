package com.assistant.core.schemas

/**
 * External JSON schemas for Zone configuration
 * Keeps schemas separate from business logic for better maintainability
 */
object ZoneSchemas {
    
    /**
     * Configuration schema for zone creation and modification
     */
    val CONFIG_SCHEMA = """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 60,
                    "description": "Zone display name"
                },
                "description": {
                    "type": "string",
                    "maxLength": 250,
                    "description": "Optional zone description"
                },
                "icon_name": {
                    "type": "string",
                    "maxLength": 60,
                    "default": "folder",
                    "description": "Icon name for this zone"
                },
                "color": {
                    "type": "string",
                    "maxLength": 60,
                    "description": "Color theme for this zone"
                }
            },
            "required": ["name"]
        }
    """.trimIndent()
}