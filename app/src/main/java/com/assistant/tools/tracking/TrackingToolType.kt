package com.assistant.tools.tracking

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.tools.base.ToolTypeContract
import com.assistant.tools.tracking.TrackingService
import com.assistant.core.services.ExecutableService
import com.assistant.core.validation.ValidationResult
import com.assistant.core.utils.NumberFormatting
import com.assistant.tools.tracking.data.TrackingDao
import com.assistant.tools.tracking.data.TrackingDatabase
import com.assistant.tools.tracking.entities.TrackingData
import com.assistant.tools.tracking.ui.TrackingConfigScreen
import com.assistant.tools.tracking.ui.TrackingScreen
import org.json.JSONObject

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
            "auto_switch": false
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
            "auto_switch": {
                "type": "boolean",
                "default": true,
                "description": "For duration type: automatically stop previous activity when starting a new one"
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
    
    override fun validateData(data: Any, operation: String): ValidationResult {
        if (data !is TrackingData) {
            return ValidationResult.error("Expected TrackingData, got ${data::class.simpleName}")
        }
        
        return when (operation) {
            "create" -> validateCreate(data)
            "update" -> validateUpdate(data)
            "delete" -> validateDelete(data)
            else -> ValidationResult.error("Unknown operation: $operation")
        }
    }
    
    private fun validateCreate(data: TrackingData): ValidationResult {
        // Validation complète pour création
        if (data.name.isBlank()) {
            return ValidationResult.error("Entry name cannot be blank")
        }
        
        if (data.tool_instance_id.isBlank()) {
            return ValidationResult.error("Tool instance ID is required")
        }
        
        if (data.zone_name.isBlank()) {
            return ValidationResult.error("Zone name is required")
        }
        
        if (data.tool_instance_name.isBlank()) {
            return ValidationResult.error("Tool instance name is required")
        }
        
        // Validation du format JSON de la valeur
        return validateValueFormat(data.value)
    }
    
    private fun validateUpdate(data: TrackingData): ValidationResult {
        // Validation plus souple pour mise à jour
        if (data.id.isBlank()) {
            return ValidationResult.error("Entry ID is required for update")
        }
        
        // Si on modifie la valeur, elle doit rester valide
        return validateValueFormat(data.value)
    }
    
    private fun validateDelete(data: TrackingData): ValidationResult {
        // Validation minimale pour suppression
        if (data.id.isBlank()) {
            return ValidationResult.error("Entry ID is required for deletion")
        }
        
        return ValidationResult.success()
    }
    
    private fun validateValueFormat(value: String): ValidationResult {
        return try {
            val json = JSONObject(value)
            
            // Strict validation: 'type' field is required
            if (!json.has("type")) {
                return ValidationResult.error("Missing required 'type' field in value JSON")
            }
            
            val type = json.getString("type")
            
            when (type) {
                "numeric" -> {
                    if (!json.has("amount")) {
                        return ValidationResult.error("Missing required 'amount' field for numeric type")
                    }
                    
                    val amount = json.getDouble("amount") // Strict: will throw if missing or invalid
                    ValidationResult.success()
                }
                // TODO: Add validation for other tracking types when implemented
                else -> ValidationResult.error("Unsupported tracking type: $type")
            }
        } catch (e: Exception) {
            ValidationResult.error("Invalid JSON format in value field: ${e.message}")
        }
    }
}