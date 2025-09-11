package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.strings.Strings

/**
 * External JSON schemas for MainScreen configuration
 * Keeps schemas separate from business logic for better maintainability
 */
object MainScreenSchemas {
    
    /**
     * Configuration schema for main screen layout and display preferences
     */
    fun getConfigSchema(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
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
            "required": []
        }
        """.trimIndent()
    }
}