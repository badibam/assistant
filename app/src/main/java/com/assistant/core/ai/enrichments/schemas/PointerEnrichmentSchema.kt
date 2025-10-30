package com.assistant.core.ai.enrichments.schemas

import android.content.Context
import com.assistant.core.strings.Strings

/**
 * Schema pour l'enrichissement Pointer (🔍)
 * Définit la structure et documentation pour référencer des données existantes
 */
object PointerEnrichmentSchema {

    fun getSchema(context: Context): String {
        val s = Strings.`for`(context = context)

        return """
    {
        "type": "object",
        "title": "${s.shared("pointer_schema_title")}",
        "description": "${s.shared("pointer_schema_description")}",
        "properties": {
            "selectedPath": {
                "type": "string",
                "description": "${s.shared("pointer_schema_selected_path")}",
                "required": true,
                "examples": [
                    "zones/health",
                    "zones/health/tools/weight_tracking",
                    "zones/health/tools/weight_tracking/fields/value"
                ]
            },
            "selectedValues": {
                "type": "array",
                "items": {"type": "string"},
                "description": "${s.shared("pointer_schema_selected_values")}",
                "required": false
            },
            "selectionLevel": {
                "type": "string",
                "description": "${s.shared("pointer_schema_selection_level")}",
                "enum": ["ZONE", "INSTANCE"],
                "required": true,
                "note": "${s.shared("pointer_schema_selection_level_note")}"
            },
            "includeData": {
                "type": "boolean",
                "description": "${s.shared("pointer_schema_include_data")}",
                "default": false,
                "note": "${s.shared("pointer_schema_include_data_note")}"
            },
            "période": {
                "type": "string",
                "description": "${s.shared("pointer_schema_periode")}",
                "required": false,
                "examples": ["last_week", "this_month", "today", "custom_range"]
            },
            "description": {
                "type": "string",
                "description": "${s.shared("pointer_schema_description_field")}",
                "required": false,
                "maxLength": 255
            }
        },
        "usage": {
            "description": "${s.shared("pointer_schema_usage_description")}",
            "capabilities": [
                "${s.shared("pointer_schema_usage_capability_1")}",
                "${s.shared("pointer_schema_usage_capability_2")}",
                "${s.shared("pointer_schema_usage_capability_3")}",
                "${s.shared("pointer_schema_usage_capability_4")}"
            ]
        },
        "commands": {
            "description": "${s.shared("pointer_schema_commands_description")}",
            "available": [
                "${s.shared("pointer_schema_command_tool_data")}",
                "${s.shared("pointer_schema_command_zones_get")}",
                "${s.shared("pointer_schema_command_tools_list")}",
                "${s.shared("pointer_schema_command_data_navigator")}"
            ]
        },
        "examples": {
            "zone_level": "${s.shared("pointer_schema_example_zone_level")}",
            "tool_level": "${s.shared("pointer_schema_example_tool_level")}",
            "field_level": "${s.shared("pointer_schema_example_field_level")}",
            "with_values": "${s.shared("pointer_schema_example_with_values")}"
        }
    }
    """
    }
}