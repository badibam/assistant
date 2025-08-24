package com.assistant.tools.tracking

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.tools.base.ToolTypeContract
import com.assistant.core.services.TrackingService
import com.assistant.core.services.ExecutableService
import com.assistant.tools.tracking.data.TrackingDao
import com.assistant.tools.tracking.data.TrackingDatabase
import com.assistant.tools.tracking.entities.TrackingData
import com.assistant.tools.tracking.ui.TrackingConfigScreen
import com.assistant.tools.tracking.ui.TrackingScreen

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
            "description": "",
            "management": "",
            "config_validation": false,
            "data_validation": false,
            "display_mode": "",
            "icon_name": "activity",
            "type": "",
            "show_value": true,
            "item_mode": "",
            "save_new_items": false,
            "auto_switch": false,
            "groups": []
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
                "values": ["numeric", "text", "scale", "boolean", "duration", "choice", "counter"],
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
            "auto_switch": {
                "type": "boolean",
                "default": true,
                "description": "For duration type: automatically stop previous activity when starting a new one"
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
        return listOf(
            "add_entry", "get_entries", "update_entry", "delete_entry",
            "start_activity", "stop_activity", "stop_all"
        )
    }
    
    override fun getDefaultIconName(): String {
        return "activity"
    }
    
    @Composable
    override fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit,
        existingConfig: String?,
        existingToolId: String?,
        onDelete: (() -> Unit)?
    ) {
        TrackingConfigScreen(
            zoneId = zoneId,
            onSave = onSave,
            onCancel = onCancel,
            existingConfig = existingConfig,
            existingToolId = existingToolId,
            onDelete = onDelete
        )
    }
    
    override fun getService(context: Context): ExecutableService {
        return TrackingService(context)
    }
    
    override fun getDao(context: Context): Any {
        return TrackingDatabase.getDatabase(context).trackingDao()
    }
    
    override fun getDatabaseEntities(): List<Class<*>> {
        return listOf(TrackingData::class.java)
    }
    
    @Composable
    override fun getUsageScreen(
        toolInstanceId: String,
        configJson: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit
    ) {
        TrackingScreen(
            toolInstanceId = toolInstanceId,
            configJson = configJson,
            onNavigateBack = onNavigateBack,
            onLongClick = onLongClick
        )
    }
}