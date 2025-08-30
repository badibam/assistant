package com.assistant.tools.tracking

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.assistant.core.tools.ToolTypeContract
import com.assistant.tools.tracking.TrackingService
import com.assistant.core.services.ExecutableService
import com.assistant.core.validation.ValidationResult
import com.assistant.core.utils.NumberFormatting
import com.assistant.tools.tracking.data.TrackingDao
import com.assistant.tools.tracking.data.TrackingDatabase
import com.assistant.tools.tracking.entities.TrackingData
import com.assistant.tools.tracking.handlers.TrackingTypeFactory
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
            "config_validation": "",
            "data_validation": "",
            "display_mode": "",
            "icon_name": "activity",
            "type": "numeric",
            "auto_switch": "",
            "items": [
                {
                    "name": "Eau",
                    "unit": "ml",
                    "default_value": "250"
                },
                {
                    "name": "Marche",
                    "unit": "min",
                    "default_value": "30"
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
                "values": ["numeric", "text", "scale", "boolean", "timer", "choice", "counter"],
                "required": true,
                "description": "Data type for all items in this tracking instance"
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
        existingToolId: String?,
        onDelete: (() -> Unit)?
    ) {
        TrackingConfigScreen(
            zoneId = zoneId,
            onSave = onSave,
            onCancel = onCancel,
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
        zoneName: String,
        onNavigateBack: () -> Unit,
        onLongClick: () -> Unit
    ) {
        TrackingScreen(
            toolInstanceId = toolInstanceId,
            zoneName = zoneName,
            onNavigateBack = onNavigateBack,
            onConfigureClick = onLongClick
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
        android.util.Log.d("TRACKING_DEBUG", "validateValueFormat called with value: $value")
        return try {
            val json = JSONObject(value)
            android.util.Log.d("TRACKING_DEBUG", "Parsed JSON successfully")
            
            // Strict validation: 'type' field is required
            if (!json.has("type")) {
                android.util.Log.e("TRACKING_DEBUG", "Missing 'type' field in JSON")
                return ValidationResult.error("Missing required 'type' field in value JSON")
            }
            
            val type = json.getString("type")
            android.util.Log.d("TRACKING_DEBUG", "Validating type: $type")
            
            // Use TrackingTypeFactory handlers for validation
            val handler = TrackingTypeFactory.getHandler(type)
            if (handler == null) {
                android.util.Log.e("TRACKING_DEBUG", "No handler found for type: $type")
                return ValidationResult.error("Unsupported tracking type: $type")
            }
            android.util.Log.d("TRACKING_DEBUG", "Handler found for type: $type")
            
            // Convert JSON back to properties map for handler validation
            val properties = jsonToProperties(json, type)
            android.util.Log.d("TRACKING_DEBUG", "Converted to properties: $properties")
            
            // Validate using the appropriate handler
            val isValid = handler.validateInput(properties)
            android.util.Log.d("TRACKING_DEBUG", "Handler validation result: $isValid")
            if (!isValid) {
                android.util.Log.e("TRACKING_DEBUG", "Handler validation FAILED for $type with properties: $properties")
                return ValidationResult.error("Invalid $type data in value JSON")
            }
            
            android.util.Log.d("TRACKING_DEBUG", "Validation SUCCESS for type: $type")
            ValidationResult.success()
        } catch (e: Exception) {
            android.util.Log.e("TRACKING_DEBUG", "Exception in validateValueFormat: ${e.message}", e)
            ValidationResult.error("Invalid JSON format in value field: ${e.message}")
        }
    }
    
    /**
     * Convert JSON value back to properties map for handler validation
     */
    fun jsonToProperties(json: JSONObject, type: String): Map<String, Any> {
        return when (type) {
            "numeric" -> mapOf(
                "quantity" to json.optString("quantity", ""),
                "unit" to json.optString("unit", "")
            )
            "boolean" -> mapOf(
                "state" to json.optBoolean("state", false),
                "true_label" to json.optString("true_label", "Oui"),
                "false_label" to json.optString("false_label", "Non")
            )
            "scale" -> mapOf(
                "value" to json.optInt("value", 0),
                "min" to json.optInt("min", 1),
                "max" to json.optInt("max", 10),
                "min_label" to json.optString("min_label", ""),
                "max_label" to json.optString("max_label", "")
            )
            "text" -> mapOf(
                "text" to json.optString("text", "")
            )
            "choice" -> {
                val availableOptions = json.optJSONArray("available_options")?.let { array ->
                    (0 until array.length()).map { array.optString(it, "") }.filter { it.isNotEmpty() }
                } ?: emptyList<String>()
                
                mapOf(
                    "selected_option" to json.optString("selected_option", ""),
                    "available_options" to availableOptions
                )
            }
            "counter" -> mapOf(
                "increment" to json.optInt("increment", 0)
            )
            "timer" -> mapOf(
                "activity" to json.optString("activity", ""),
                "duration_minutes" to json.optInt("duration_minutes", 0)
            )
            else -> emptyMap()
        }
    }
    
    override fun getDatabaseMigrations(): List<Migration> {
        return listOf(
            // Exemple de migration future pour TrackingData
            // TRACKING_MIGRATION_1_2
        )
    }
    
    override fun migrateConfig(fromVersion: Int, configJson: String): String {
        return when (fromVersion) {
            1 -> {
                // Exemple: Migration config v1 → v2
                // val config = JSONObject(configJson)
                // config.put("new_field", "default_value")
                // config.toString()
                configJson // Pas de migration nécessaire pour l'instant
            }
            else -> configJson // Pas de migration connue
        }
    }
    
    // Migrations d'exemple (pour référence future)
    private object Migrations {
        // Exemple de migration de base de données (pour référence future)
        /*
        val TRACKING_MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Exemple: Ajouter une colonne
                database.execSQL("ALTER TABLE tracking_data ADD COLUMN category TEXT DEFAULT ''")
                
                // Exemple: Migrer des données
                database.execSQL("UPDATE tracking_data SET category = 'health' WHERE tool_instance_name LIKE '%santé%'")
            }
        }
        */
    }
}