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
                                        "name": { "type": "string", "minLength": 1, "maxLength": 60 },
                                        "default_quantity": { "type": "number" },
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
                                        "name": { "type": "string", "minLength": 1, "maxLength": 60 }
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
                                        "name": { "type": "string", "minLength": 1, "maxLength": 60 }
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
                                        "name": { "type": "string", "minLength": 1, "maxLength": 60 },
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
                                        "name": { "type": "string", "minLength": 1, "maxLength": 60 }
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
                                        "name": { "type": "string", "minLength": 1, "maxLength": 60 },
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
                                        "name": { "type": "string", "minLength": 1, "maxLength": 60 }
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
    
    override fun getDataSchema(): String? {
        // Tracking-specific data schema extending base schema
        val specificSchema = """
        {
            "properties": {
                "id": { "type": "string" },
                "tool_instance_id": { "type": "string" },
                "zone_name": { "type": "string" },
                "tool_instance_name": { "type": "string" },
                "name": { "type": "string", "minLength": 1, "maxLength": 60 },
                "recorded_at": { "type": "number" },
                "value": {
                    "type": "object",
                    "description": "Tracking data specific to the tracking type",
                    "properties": {
                        "type": { "type": "string" },
                        "raw": { "type": "string", "maxLength": 250 }
                    },
                    "required": ["type"],
                    "additionalProperties": false
                }
            },
            "required": ["name", "value"],
            "allOf": [
                {
                    "if": {
                        "properties": { 
                            "value": { 
                                "properties": { "type": { "const": "numeric" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "value": {
                                "properties": {
                                    "type": { "const": "numeric" },
                                    "quantity": { "type": "number" },
                                    "unit": { "type": "string", "maxLength": 60 },
                                    "raw": { "type": "string", "maxLength": 250 }
                                },
                                "required": ["type", "quantity"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "value": { 
                                "properties": { "type": { "const": "text" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "value": {
                                "properties": {
                                    "type": { "const": "text" },
                                    "text": { "type": "string", "maxLength": 250 },
                                    "raw": { "type": "string", "maxLength": 250 }
                                },
                                "required": ["type", "text"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "value": { 
                                "properties": { "type": { "const": "scale" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "value": {
                                "properties": {
                                    "type": { "const": "scale" },
                                    "rating": { "type": "integer", "minimum": 1, "maximum": 100 },
                                    "min_value": { "type": "integer", "minimum": 1 },
                                    "max_value": { "type": "integer", "maximum": 100 },
                                    "min_label": { "type": "string", "maxLength": 60 },
                                    "max_label": { "type": "string", "maxLength": 60 },
                                    "raw": { "type": "string", "maxLength": 250 }
                                },
                                "required": ["type", "rating", "min_value", "max_value"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "value": { 
                                "properties": { "type": { "const": "boolean" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "value": {
                                "properties": {
                                    "type": { "const": "boolean" },
                                    "state": { "type": "boolean" },
                                    "true_label": { "type": "string", "maxLength": 60 },
                                    "false_label": { "type": "string", "maxLength": 60 },
                                    "raw": { "type": "string", "maxLength": 250 }
                                },
                                "required": ["type", "state"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "value": { 
                                "properties": { "type": { "const": "choice" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "value": {
                                "properties": {
                                    "type": { "const": "choice" },
                                    "selected_option": { "type": "string", "maxLength": 60 },
                                    "available_options": {
                                        "type": "array",
                                        "items": { "type": "string", "maxLength": 60 }
                                    },
                                    "raw": { "type": "string", "maxLength": 250 }
                                },
                                "required": ["type", "selected_option", "available_options"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "value": { 
                                "properties": { "type": { "const": "counter" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "value": {
                                "properties": {
                                    "type": { "const": "counter" },
                                    "increment": { "type": "integer" },
                                    "raw": { "type": "string", "maxLength": 250 }
                                },
                                "required": ["type", "increment"],
                                "additionalProperties": false
                            }
                        }
                    }
                },
                {
                    "if": {
                        "properties": { 
                            "value": { 
                                "properties": { "type": { "const": "timer" } } 
                            } 
                        }
                    },
                    "then": {
                        "properties": {
                            "value": {
                                "properties": {
                                    "type": { "const": "timer" },
                                    "activity": { "type": "string", "maxLength": 60 },
                                    "duration_minutes": { "type": "integer", "minimum": 0 },
                                    "raw": { "type": "string", "maxLength": 250 }
                                },
                                "required": ["type", "activity", "duration_minutes"],
                                "additionalProperties": false
                            }
                        }
                    }
                }
            ]
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
        // Legacy method - validation is now handled by SchemaValidator V3
        // This method is kept for compatibility but all validation is centralized
        return ValidationResult.success()
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
    
    /**
     * Converts TrackingData to JSON for schema validation
     * This creates a JSON representation that matches the data schema
     */
    fun TrackingData.toValidationJson(): String {
        return JSONObject().apply {
            put("id", id)
            put("tool_instance_id", tool_instance_id)
            put("zone_name", zone_name)
            put("tool_instance_name", tool_instance_name)
            put("name", name)
            // Parse value string JSON into object for schema validation
            val valueObject = JSONObject(value)
            put("value", valueObject)
            put("recorded_at", recorded_at)
        }.toString()
    }
    
    /**
     * Converts validated JSON back to TrackingData
     * Used after successful schema validation
     */
    fun String.toTrackingData(): TrackingData {
        val json = JSONObject(this)
        return TrackingData(
            id = json.optString("id", ""),
            tool_instance_id = json.getString("tool_instance_id"),
            zone_name = json.getString("zone_name"),
            tool_instance_name = json.getString("tool_instance_name"),
            name = json.getString("name"),
            value = json.getString("value"),
            recorded_at = json.getLong("recorded_at")
        )
    }
    
    /**
     * Get user-friendly field name for display
     * @param fieldName The technical field name (e.g., "quantity", "name")
     * @return User-friendly field name for display (e.g., "Quantité", "Nom")
     */
    override fun getFormFieldName(fieldName: String): String {
        return when(fieldName) {
            "default_quantity" -> "Qté par défaut"
            "quantity" -> "Quantité"
            "name" -> "Nom" 
            "unit" -> "Unité"
            "text" -> "Texte"
            "rating" -> "Note"
            "min_value" -> "Valeur min"
            "max_value" -> "Valeur max"
            "min_label" -> "Libellé min"
            "max_label" -> "Libellé max"
            "state" -> "État"
            "true_label" -> "Libellé vrai"
            "false_label" -> "Libellé faux"
            "selected_option" -> "Option sélectionnée"
            "available_options" -> "Options disponibles"
            "increment" -> "Incrément"
            "activity" -> "Activité"
            "duration_minutes" -> "Durée"
            else -> "Champ non reconnu"
        }
    }
}