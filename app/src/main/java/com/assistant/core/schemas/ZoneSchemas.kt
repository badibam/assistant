package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.strings.Strings

/**
 * External JSON schemas for Zone configuration
 * Keeps schemas separate from business logic for better maintainability
 */
object ZoneSchemas {
    
    /**
     * Configuration schema for zone creation and modification
     */
    fun getConfigSchema(context: Context): String {
        val s = Strings.`for`(context = context)
        return """
        {
            "x-schema-id": "zone_config",
            "x-schema-display-name": "${s.shared("zone_config_schema_display_name")}",
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 60,
                    "description": "${s.shared("zone_schema_name")}"
                },
                "description": {
                    "type": "string",
                    "maxLength": 250,
                    "description": "${s.shared("zone_schema_description")}"
                },
                "icon_name": {
                    "type": "string",
                    "maxLength": 60,
                    "default": "folder",
                    "description": "${s.shared("zone_schema_icon_name")}"
                },
                "color": {
                    "type": "string",
                    "maxLength": 60,
                    "description": "${s.shared("zone_schema_color")}"
                }
            },
            "required": ["name"]
        }
        """.trimIndent()
    }
}