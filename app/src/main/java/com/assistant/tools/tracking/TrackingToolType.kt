package com.assistant.tools.tracking

import androidx.compose.runtime.Composable
import com.assistant.core.tools.base.ToolTypeContract
import com.assistant.tools.tracking.entities.TrackingData
import com.assistant.tools.tracking.ui.TrackingConfigScreen

/**
 * Tracking Tool Type implementation
 * Provides static metadata for tracking tool instances
 */
object TrackingToolType : ToolTypeContract {
    
    override fun getDisplayName(): String = "Suivi"
    
    override fun getDefaultConfig(): String {
        return """
        {
            "name": "",
            "type": "numeric",
            "show_value": true,
            "item_mode": "free",
            "save_new_items": false,
            "default_unit": "",
            "min_value": null,
            "max_value": null,
            "groups": [
                {
                    "name": "Default",
                    "items": []
                }
            ]
        }
        """.trimIndent()
    }
    
    override fun getConfigSchema(): String {
        // TODO: Define custom JSON Schema extension fields for AI and UI context
        // Examples: "x-ai-context", "x-ui-hint", "x-purpose", "x-validation-rules"
        return """
        {
            "name": {
                "type": "string",
                "required": true,
                "description": "Display name for this tracking instance"
            },
            "type": {
                "type": "enum",
                "values": ["numeric", "text", "scale", "boolean"],
                "required": true,
                "description": "Data type for all items in this tracking instance"
            },
            "show_value": {
                "type": "boolean",
                "default": true,
                "description": "Whether to show value input field in UI"
            },
            "item_mode": {
                "type": "enum",
                "values": ["free", "predefined", "both"],
                "default": "free",
                "description": "Item input mode: free text, predefined list, or both"
            },
            "save_new_items": {
                "type": "boolean",
                "default": false,
                "description": "Whether to save new free-text items to predefined list"
            },
            "default_unit": {
                "type": "string",
                "description": "Default unit for numeric values"
            },
            "min_value": {
                "type": "number",
                "description": "Minimum allowed value for numeric type"
            },
            "max_value": {
                "type": "number",
                "description": "Maximum allowed value for numeric type"
            },
            "groups": {
                "type": "array",
                "required": true,
                "min_items": 1,
                "description": "Groups containing predefined items",
                "items": {
                    "type": "object",
                    "properties": {
                        "name": {
                            "type": "string",
                            "required": true,
                            "description": "Group display name"
                        },
                        "items": {
                            "type": "array",
                            "description": "Predefined items in this group",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "name": {
                                        "type": "string",
                                        "required": true,
                                        "description": "Item display name"
                                    },
                                    "unit": {
                                        "type": "string",
                                        "description": "Unit for this specific item"
                                    },
                                    "default_amount": {
                                        "type": "number",
                                        "description": "Default amount/value for this item"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        """.trimIndent()
    }
    
    override fun getAvailableOperations(): List<String> {
        return listOf("add_entry", "get_entries", "update_entry", "delete_entry")
    }
    
    override fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit
    ): @Composable () -> Unit {
        return {
            TrackingConfigScreen(
                zoneId = zoneId,
                onSave = onSave,
                onCancel = onCancel
            )
        }
    }
}