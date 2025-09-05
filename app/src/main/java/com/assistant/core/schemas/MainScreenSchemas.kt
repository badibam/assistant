package com.assistant.core.schemas

/**
 * External JSON schemas for MainScreen configuration
 * Keeps schemas separate from business logic for better maintainability
 */
object MainScreenSchemas {
    
    /**
     * Configuration schema for main screen layout and display preferences
     */
    val CONFIG_SCHEMA = """
        {
            "type": "object",
            "properties": {
                "zone_display_mode": {
                    "type": "string",
                    "enum": ["list", "grid", "compact"],
                    "default": "list",
                    "description": "How zones are displayed on the main screen"
                },
                "show_zone_descriptions": {
                    "type": "boolean",
                    "default": true,
                    "description": "Whether to show zone descriptions"
                },
                "zones_per_row": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 4,
                    "default": 2,
                    "description": "Number of zones per row in grid mode"
                },
                "enable_quick_actions": {
                    "type": "boolean",
                    "default": true,
                    "description": "Enable quick action buttons on zones"
                }
            },
            "required": []
        }
    """.trimIndent()
}