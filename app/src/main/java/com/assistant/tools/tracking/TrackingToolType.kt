package com.assistant.tools.tracking

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.tools.BaseSchemas
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
            "items": []
        }
        """.trimIndent()
    }
    
    override fun getConfigSchema(): String {
        // Tracking-specific configuration schema extending base schema
        val specificSchema = """
        {
            "properties": {
                "type": {
                    "type": "string",
                    "enum": ["numeric", "text", "scale", "boolean", "timer", "choice", "counter"],
                    "description": "Data type for all items in this tracking instance"
                }
            },
            "required": ["type"],
            "allOf": [
                {
                    "if": {
                        "properties": { "type": { "const": "numeric" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "Predefined numeric items with quantity and unit",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "maxLength": 60 },
                                        "default_quantity": { "type": "string", "maxLength": 60 },
                                        "unit": { "type": "string", "maxLength": 60 }
                                    },
                                    "required": ["name"]
                                }
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "timer" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "Predefined timer activities",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "maxLength": 60 }
                                    },
                                    "required": ["name"]
                                }
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "choice" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "Available choice options",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "maxLength": 60 }
                                    },
                                    "required": ["name"]
                                }
                            }
                        },
                        "required": ["items"]
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "scale" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "Predefined scale items with min/max values",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "maxLength": 60 },
                                        "min": { "type": "integer", "default": 1 },
                                        "max": { "type": "integer", "default": 10 },
                                        "min_label": { "type": "string", "maxLength": 60 },
                                        "max_label": { "type": "string", "maxLength": 60 }
                                    },
                                    "required": ["name"]
                                }
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "counter" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "Predefined counter items",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "maxLength": 60 }
                                    },
                                    "required": ["name"]
                                }
                            },
                            "allow_decrement": {
                                "type": "boolean",
                                "default": true,
                                "description": "Allow negative increments"
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "boolean" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "Predefined boolean items with custom labels",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "maxLength": 60 },
                                        "true_label": { "type": "string", "maxLength": 60, "default": "Oui" },
                                        "false_label": { "type": "string", "maxLength": 60, "default": "Non" }
                                    },
                                    "required": ["name"]
                                }
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { "type": { "const": "text" } }
                    },
                    "then": {
                        "properties": {
                            "items": {
                                "type": "array",
                                "description": "Predefined text items",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "name": { "type": "string", "maxLength": 60 }
                                    },
                                    "required": ["name"]
                                }
                            }
                        }
                    }
                }
            ]
        }
        """.trimIndent()
        
        // Combine base schema with tracking-specific schema
        return BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(),
            specificSchema
        )
    }
    
    override fun getDataSchema(): String {
        // Tracking-specific data schema extending base schema
        val specificSchema = """
        {
            "properties": {
                "value": {
                    "type": "string",
                    "description": "JSON-encoded tracking data specific to the tracking type",
                    "oneOf": [
                        {
                            "description": "Numeric tracking data",
                            "additionalProperties": false,
                            "properties": {
                                "type": { "const": "numeric" },
                                "quantity": { "type": "string", "maxLength": 60 },
                                "unit": { "type": "string", "maxLength": 60 },
                                "raw": { "type": "string", "maxLength": 250 }
                            },
                            "required": ["type", "quantity"]
                        },
                        {
                            "description": "Text tracking data", 
                            "additionalProperties": false,
                            "properties": {
                                "type": { "const": "text" },
                                "text": { "type": "string", "maxLength": 250 },
                                "raw": { "type": "string", "maxLength": 250 }
                            },
                            "required": ["type", "text"]
                        },
                        {
                            "description": "Scale tracking data",
                            "additionalProperties": false,
                            "properties": {
                                "type": { "const": "scale" },
                                "value": { "type": "integer", "minimum": 1, "maximum": 100 },
                                "min_value": { "type": "integer", "minimum": 1 },
                                "max_value": { "type": "integer", "maximum": 100 },
                                "min_label": { "type": "string", "maxLength": 60 },
                                "max_label": { "type": "string", "maxLength": 60 },
                                "raw": { "type": "string", "maxLength": 250 }
                            },
                            "required": ["type", "value", "min_value", "max_value"]
                        },
                        {
                            "description": "Boolean tracking data",
                            "additionalProperties": false,
                            "properties": {
                                "type": { "const": "boolean" },
                                "state": { "type": "boolean" },
                                "true_label": { "type": "string", "maxLength": 60 },
                                "false_label": { "type": "string", "maxLength": 60 },
                                "raw": { "type": "string", "maxLength": 250 }
                            },
                            "required": ["type", "state"]
                        },
                        {
                            "description": "Choice tracking data",
                            "additionalProperties": false,
                            "properties": {
                                "type": { "const": "choice" },
                                "selected_option": { "type": "string", "maxLength": 60 },
                                "available_options": {
                                    "type": "array",
                                    "items": { "type": "string", "maxLength": 60 }
                                },
                                "raw": { "type": "string", "maxLength": 250 }
                            },
                            "required": ["type", "selected_option", "available_options"]
                        },
                        {
                            "description": "Counter tracking data",
                            "additionalProperties": false,
                            "properties": {
                                "type": { "const": "counter" },
                                "increment": { "type": "integer" },
                                "raw": { "type": "string", "maxLength": 250 }
                            },
                            "required": ["type", "increment"]
                        },
                        {
                            "description": "Timer tracking data",
                            "additionalProperties": false,
                            "properties": {
                                "type": { "const": "timer" },
                                "activity": { "type": "string", "maxLength": 60 },
                                "duration_minutes": { "type": "integer", "minimum": 0 },
                                "raw": { "type": "string", "maxLength": 250 }
                            },
                            "required": ["type", "activity", "duration_minutes"]
                        }
                    ]
                }
            },
            "required": ["value"]
        }
        """.trimIndent()
        
        // Combine base schema with tracking-specific schema
        return BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(),
            specificSchema
        )
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