package com.assistant.tools.tracking

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.sqlite.db.SupportSQLiteDatabase
import com.assistant.core.tools.ToolTypeContract
import com.assistant.core.services.ExecutableService
import com.assistant.core.validation.ValidationResult
import com.assistant.core.utils.NumberFormatting
import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory
import com.assistant.core.validation.SchemaProvider
import com.assistant.core.validation.FieldLimits
import com.assistant.core.tools.BaseSchemas
import com.assistant.tools.tracking.ui.TrackingConfigScreen
import com.assistant.tools.tracking.ui.TrackingScreen
import org.json.JSONObject

/**
 * Tracking Tool Type implementation
 * Provides static metadata for tracking tool instances
 */
object TrackingToolType : ToolTypeContract, SchemaProvider {
    
    override fun getDisplayName(context: Context): String {
        val s = Strings.`for`(tool = "tracking", context = context)
        return s.tool("display_name")
    }

    override fun getDescription(context: Context): String {
        val s = Strings.`for`(tool = "tracking", context = context)
        return s.tool("description")
    }

    override fun getDefaultConfig(): String {
        return """
        {
            "type": "numeric",
            "items": [],
            "name": "",
            "description": "",
            "icon_name": "activity",
            "management": "manual",
            "validateConfig": false,
            "validateData": false,
            "always_send": false,
            "display_mode": "LINE"
        }
        """.trimIndent()
    }

    override fun getSchema(schemaId: String, context: Context): Schema? {
        return when(schemaId) {
            "tracking_config_numeric" -> createConfigNumericSchema(context)
            "tracking_config_scale" -> createConfigScaleSchema(context)
            "tracking_config_boolean" -> createConfigBooleanSchema(context)
            "tracking_config_choice" -> createConfigChoiceSchema(context)
            "tracking_config_counter" -> createConfigCounterSchema(context)
            "tracking_config_timer" -> createConfigTimerSchema(context)
            "tracking_config_text" -> createConfigTextSchema(context)
            "tracking_data_numeric" -> createDataNumericSchema(context)
            "tracking_data_scale" -> createDataScaleSchema(context)
            "tracking_data_boolean" -> createDataBooleanSchema(context)
            "tracking_data_choice" -> createDataChoiceSchema(context)
            "tracking_data_counter" -> createDataCounterSchema(context)
            "tracking_data_timer" -> createDataTimerSchema(context)
            "tracking_data_text" -> createDataTextSchema(context)
            else -> null
        }
    }

    override fun getAllSchemaIds(): List<String> {
        return listOf(
            "tracking_config_numeric", "tracking_config_scale", "tracking_config_boolean",
            "tracking_config_choice", "tracking_config_counter", "tracking_config_timer", "tracking_config_text",
            "tracking_data_numeric", "tracking_data_scale", "tracking_data_boolean",
            "tracking_data_choice", "tracking_data_counter", "tracking_data_timer", "tracking_data_text"
        )
    }

    private fun createConfigNumericSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "type": {
                    "type": "string",
                    "const": "numeric",
                    "description": "${s.tool("schema_config_type")}"
                },
                "items": {
                    "type": "array",
                    "description": "${s.tool("schema_config_numeric_items")}",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {
                                "type": "string",
                                "minLength": 1,
                                "maxLength": ${FieldLimits.SHORT_LENGTH},
                                "description": "${s.tool("schema_config_numeric_item_name")}"
                            },
                            "default_quantity": {
                                "type": "number",
                                "description": "${s.tool("schema_config_numeric_item_default_quantity")}"
                            },
                            "unit": {
                                "type": "string",
                                "maxLength": ${FieldLimits.SHORT_LENGTH},
                                "description": "${s.tool("schema_config_numeric_item_unit")}"
                            }
                        },
                        "required": ["name"]
                    }
                }
            },
            "required": ["type"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_config_numeric",
            displayName = s.tool("schema_config_numeric_display_name"),
            description = s.tool("schema_config_numeric_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = content
        )
    }

    private fun createConfigScaleSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "type": {
                    "type": "string",
                    "const": "scale",
                    "description": "${s.tool("schema_config_type")}"
                },
                "items": {
                    "type": "array",
                    "description": "${s.tool("schema_config_scale_items")}",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {
                                "type": "string",
                                "minLength": 1,
                                "maxLength": ${FieldLimits.SHORT_LENGTH},
                                "description": "${s.tool("schema_config_scale_item_name")}"
                            }
                        },
                        "required": ["name"]
                    }
                },
                "min": {
                    "type": "integer",
                    "default": 1,
                    "description": "${s.tool("schema_config_scale_min")}"
                },
                "max": {
                    "type": "integer",
                    "default": 10,
                    "description": "${s.tool("schema_config_scale_max")}"
                },
                "min_label": {
                    "type": "string",
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.tool("schema_config_scale_min_label")}"
                },
                "max_label": {
                    "type": "string",
                    "maxLength": ${FieldLimits.SHORT_LENGTH},
                    "description": "${s.tool("schema_config_scale_max_label")}"
                }
            },
            "required": ["type"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_config_scale",
            displayName = s.tool("schema_config_scale_display_name"),
            description = s.tool("schema_config_scale_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = content
        )
    }

    private fun createConfigBooleanSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "type": {
                    "type": "string",
                    "const": "boolean",
                    "description": "${s.tool("schema_config_type")}"
                },
                "items": {
                    "type": "array",
                    "description": "${s.tool("schema_config_boolean_items")}",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {
                                "type": "string",
                                "minLength": 1,
                                "maxLength": ${FieldLimits.SHORT_LENGTH},
                                "description": "${s.tool("schema_config_boolean_item_name")}"
                            },
                            "true_label": {
                                "type": "string",
                                "maxLength": ${FieldLimits.SHORT_LENGTH},
                                "description": "${s.tool("schema_config_boolean_item_true_label")}"
                            },
                            "false_label": {
                                "type": "string",
                                "maxLength": ${FieldLimits.SHORT_LENGTH},
                                "description": "${s.tool("schema_config_boolean_item_false_label")}"
                            }
                        },
                        "required": ["name", "true_label", "false_label"]
                    }
                }
            },
            "required": ["type"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_config_boolean",
            displayName = s.tool("schema_config_boolean_display_name"),
            description = s.tool("schema_config_boolean_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = content
        )
    }

    private fun createConfigChoiceSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "type": {
                    "type": "string",
                    "const": "choice",
                    "description": "${s.tool("schema_config_type")}"
                },
                "options": {
                    "type": "array",
                    "description": "${s.tool("schema_config_choice_options")}",
                    "items": {
                        "type": "string",
                        "minLength": 1,
                        "maxLength": ${FieldLimits.SHORT_LENGTH}
                    }
                }
            },
            "required": ["type", "options"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_config_choice",
            displayName = s.tool("schema_config_choice_display_name"),
            description = s.tool("schema_config_choice_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = content
        )
    }

    private fun createConfigCounterSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "type": {
                    "type": "string",
                    "const": "counter",
                    "description": "${s.tool("schema_config_type")}"
                },
                "items": {
                    "type": "array",
                    "description": "${s.tool("schema_config_counter_items")}",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {
                                "type": "string",
                                "minLength": 1,
                                "maxLength": ${FieldLimits.SHORT_LENGTH},
                                "description": "${s.tool("schema_config_counter_item_name")}"
                            }
                        },
                        "required": ["name"]
                    }
                },
                "allow_decrement": {
                    "type": "boolean",
                    "default": true,
                    "description": "${s.tool("schema_config_counter_allow_decrement")}"
                }
            },
            "required": ["type"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_config_counter",
            displayName = s.tool("schema_config_counter_display_name"),
            description = s.tool("schema_config_counter_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = content
        )
    }

    private fun createConfigTimerSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "type": {
                    "type": "string",
                    "const": "timer",
                    "description": "${s.tool("schema_config_type")}"
                },
                "items": {
                    "type": "array",
                    "description": "${s.tool("schema_config_timer_items")}",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {
                                "type": "string",
                                "minLength": 1,
                                "maxLength": ${FieldLimits.SHORT_LENGTH},
                                "description": "${s.tool("schema_config_timer_item_name")}"
                            }
                        },
                        "required": ["name"]
                    }
                }
            },
            "required": ["type"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_config_timer",
            displayName = s.tool("schema_config_timer_display_name"),
            description = s.tool("schema_config_timer_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = content
        )
    }

    private fun createConfigTextSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "type": {
                    "type": "string",
                    "const": "text",
                    "description": "${s.tool("schema_config_type")}"
                },
                "items": {
                    "type": "array",
                    "description": "${s.tool("schema_config_text_items")}",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {
                                "type": "string",
                                "minLength": 1,
                                "maxLength": ${FieldLimits.SHORT_LENGTH},
                                "description": "${s.tool("schema_config_text_item_name")}"
                            }
                        },
                        "required": ["name"]
                    }
                }
            },
            "required": ["type"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseConfigSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_config_text",
            displayName = s.tool("schema_config_text_display_name"),
            description = s.tool("schema_config_text_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = content
        )
    }

    private fun createDataNumericSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "data": {
                    "type": "object",
                    "properties": {
                        "type": {
                            "type": "string",
                            "const": "numeric",
                            "description": "${s.tool("schema_data_type")}"
                        },
                        "quantity": {
                            "type": "number",
                            "description": "${s.tool("schema_data_numeric_quantity")}"
                        },
                        "unit": {
                            "type": "string",
                            "maxLength": ${FieldLimits.SHORT_LENGTH},
                            "description": "${s.tool("schema_data_numeric_unit")}"
                        }
                    },
                    "required": ["type", "quantity"],
                    "additionalProperties": false
                }
            },
            "required": ["data"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_data_numeric",
            displayName = s.tool("schema_data_numeric_display_name"),
            description = s.tool("schema_data_numeric_description"),
            category = SchemaCategory.TOOL_DATA,
            content = content
        )
    }

    private fun createDataScaleSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "data": {
                    "type": "object",
                    "properties": {
                        "type": {
                            "type": "string",
                            "const": "scale",
                            "description": "${s.tool("schema_data_type")}"
                        },
                        "rating": {
                            "type": "integer",
                            "description": "${s.tool("schema_data_scale_rating")}"
                        },
                        "min_value": {
                            "type": "integer",
                            "description": "${s.tool("schema_data_scale_min_value")}"
                        },
                        "max_value": {
                            "type": "integer",
                            "description": "${s.tool("schema_data_scale_max_value")}"
                        },
                        "min_label": {
                            "type": "string",
                            "maxLength": ${FieldLimits.SHORT_LENGTH},
                            "description": "${s.tool("schema_data_scale_min_label")}"
                        },
                        "max_label": {
                            "type": "string",
                            "maxLength": ${FieldLimits.SHORT_LENGTH},
                            "description": "${s.tool("schema_data_scale_max_label")}"
                        }
                    },
                    "required": ["type", "rating", "min_value", "max_value"],
                    "additionalProperties": false
                }
            },
            "required": ["data"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_data_scale",
            displayName = s.tool("schema_data_scale_display_name"),
            description = s.tool("schema_data_scale_description"),
            category = SchemaCategory.TOOL_DATA,
            content = content
        )
    }

    private fun createDataBooleanSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "data": {
                    "type": "object",
                    "properties": {
                        "type": {
                            "type": "string",
                            "const": "boolean",
                            "description": "${s.tool("schema_data_type")}"
                        },
                        "state": {
                            "type": "boolean",
                            "description": "${s.tool("schema_data_boolean_state")}"
                        },
                        "true_label": {
                            "type": "string",
                            "maxLength": ${FieldLimits.SHORT_LENGTH},
                            "description": "${s.tool("schema_data_boolean_true_label")}"
                        },
                        "false_label": {
                            "type": "string",
                            "maxLength": ${FieldLimits.SHORT_LENGTH},
                            "description": "${s.tool("schema_data_boolean_false_label")}"
                        }
                    },
                    "required": ["type", "state"],
                    "additionalProperties": false
                }
            },
            "required": ["data"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_data_boolean",
            displayName = s.tool("schema_data_boolean_display_name"),
            description = s.tool("schema_data_boolean_description"),
            category = SchemaCategory.TOOL_DATA,
            content = content
        )
    }

    private fun createDataChoiceSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "data": {
                    "type": "object",
                    "properties": {
                        "type": {
                            "type": "string",
                            "const": "choice",
                            "description": "${s.tool("schema_data_type")}"
                        },
                        "selected_option": {
                            "type": "string",
                            "maxLength": ${FieldLimits.SHORT_LENGTH},
                            "description": "${s.tool("schema_data_choice_selected_option")}"
                        },
                        "available_options": {
                            "type": "array",
                            "description": "${s.tool("schema_data_choice_available_options")}",
                            "items": {
                                "type": "string",
                                "maxLength": ${FieldLimits.SHORT_LENGTH}
                            }
                        }
                    },
                    "required": ["type", "selected_option", "available_options"],
                    "additionalProperties": false
                }
            },
            "required": ["data"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_data_choice",
            displayName = s.tool("schema_data_choice_display_name"),
            description = s.tool("schema_data_choice_description"),
            category = SchemaCategory.TOOL_DATA,
            content = content
        )
    }

    private fun createDataCounterSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "data": {
                    "type": "object",
                    "properties": {
                        "type": {
                            "type": "string",
                            "const": "counter",
                            "description": "${s.tool("schema_data_type")}"
                        },
                        "increment": {
                            "type": "integer",
                            "description": "${s.tool("schema_data_counter_increment")}"
                        }
                    },
                    "required": ["type", "increment"],
                    "additionalProperties": false
                }
            },
            "required": ["data"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_data_counter",
            displayName = s.tool("schema_data_counter_display_name"),
            description = s.tool("schema_data_counter_description"),
            category = SchemaCategory.TOOL_DATA,
            content = content
        )
    }

    private fun createDataTimerSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "data": {
                    "type": "object",
                    "properties": {
                        "type": {
                            "type": "string",
                            "const": "timer",
                            "description": "${s.tool("schema_data_type")}"
                        },
                        "duration_seconds": {
                            "type": "integer",
                            "minimum": 0,
                            "description": "${s.tool("schema_data_timer_duration_seconds")}"
                        }
                    },
                    "required": ["type", "duration_seconds"],
                    "additionalProperties": false
                }
            },
            "required": ["data"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_data_timer",
            displayName = s.tool("schema_data_timer_display_name"),
            description = s.tool("schema_data_timer_description"),
            category = SchemaCategory.TOOL_DATA,
            content = content
        )
    }

    private fun createDataTextSchema(context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val specificSchema = """
        {
            "properties": {
                "data": {
                    "type": "object",
                    "properties": {
                        "type": {
                            "type": "string",
                            "const": "text",
                            "description": "${s.tool("schema_data_type")}"
                        },
                        "text": {
                            "type": "string",
                            "maxLength": ${FieldLimits.LONG_LENGTH},
                            "description": "${s.tool("schema_data_text_content")}"
                        }
                    },
                    "required": ["type", "text"],
                    "additionalProperties": false
                }
            },
            "required": ["data"]
        }
        """.trimIndent()

        val content = BaseSchemas.createExtendedSchema(
            BaseSchemas.getBaseDataSchema(context),
            specificSchema
        )

        return Schema(
            id = "tracking_data_text",
            displayName = s.tool("schema_data_text_display_name"),
            description = s.tool("schema_data_text_description"),
            category = SchemaCategory.TOOL_DATA,
            content = content
        )
    }

    override fun getAvailableOperations(): List<String> {
        return listOf(
            "add_entry", "get_entries", "update_entry", "delete_entry", "delete_all_entries",
            "start_activity", "stop_activity", "stop_all"
        )
    }
    
    override fun getDefaultIconName(): String {
        return "activity"
    }
    
    override fun getSuggestedIcons(): List<String> {
        return listOf("activity", "trending-up")
    }
    
    @Composable
    override fun getConfigScreen(
        zoneId: String,
        onSave: (config: String) -> Unit,
        onCancel: () -> Unit,
        existingToolId: String?,
        onDelete: (() -> Unit)?,
        initialGroup: String?
    ) {
        TrackingConfigScreen(
            zoneId = zoneId,
            onSave = onSave,
            initialGroup = initialGroup,
            onCancel = onCancel,
            existingToolId = existingToolId,
            onDelete = onDelete
        )
    }
    
    override fun getService(context: Context): ExecutableService {
        return com.assistant.core.services.ToolDataService(context)
    }
    
    override fun getDao(context: Context): Any {
        val database = com.assistant.core.database.AppDatabase.getDatabase(context)
        val baseDao = database.toolDataDao()
        
        // Uses generic implementation which is sufficient for standard tracking
        return com.assistant.core.database.dao.DefaultExtendedToolDataDao(baseDao, "tracking")
    }
    
    override fun getDatabaseEntities(): List<Class<*>> {
        return listOf(ToolDataEntity::class.java)
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
    
    
    /**
     * Get user-friendly field name for display
     * @param fieldName The technical field name (e.g., "quantity", "name")
     * @param context Android context for string resource access
     * @return User-friendly field name for display (e.g., "Quantity", "Name")
     */
    override fun getFormFieldName(fieldName: String, context: Context): String {

        val s = Strings.`for`(tool = "tracking", context = context)

        // Try common fields for all tooltypes first
        val commonFieldName = BaseSchemas.getCommonFieldName(fieldName, context)
        if (commonFieldName != null) return commonFieldName

        // Then tracking-specific fields
        return when(fieldName) {
            "default_quantity" -> s.tool("field_default_quantity")
            "quantity" -> s.tool("field_quantity")
            "unit" -> s.tool("field_unit")
            "text" -> s.tool("field_text")
            "rating" -> s.tool("field_rating")
            "min_value" -> s.tool("field_min_value")
            "max_value" -> s.tool("field_max_value")
            "min_label" -> s.tool("field_min_label")
            "max_label" -> s.tool("field_max_label")
            "state" -> s.tool("field_state")
            "true_label" -> s.tool("field_true_label")
            "false_label" -> s.tool("field_false_label")
            "selected_option" -> s.tool("field_selected_option")
            "available_options" -> s.tool("field_available_options")
            "increment" -> s.tool("field_increment")
            "activity" -> s.tool("field_activity")
            "duration_seconds" -> s.tool("field_duration_seconds")
            "type" -> s.tool("field_type")
            "raw" -> s.tool("field_raw")
            else -> s.tool("field_unknown")
        }
    }

    /**
     * Enrich tracking data by calculating the 'raw' display field
     * The 'raw' field is an auto-generated human-readable representation of the data
     *
     * Important: The 'raw' field should NOT be provided by AI/user - it's always calculated here
     * If 'raw' is present in dataJson, it will be removed and recalculated
     *
     * @param dataJson The data JSON to enrich
     * @param name The entry name (optional, used for some types)
     * @param configJson The tool instance config (optional, used for default labels)
     * @return Enriched data JSON with 'raw' field added
     * @throws Exception if enrichment fails (parsing errors, missing required fields, etc.)
     */
    override fun enrichData(dataJson: String, name: String?, configJson: String?): String {
        try {
            val dataObj = JSONObject(dataJson)

            // Remove raw if explicitly provided (auto-generated field only)
            if (dataObj.has("raw")) {
                dataObj.remove("raw")
            }

            // Calculate raw based on type
            val trackingType = dataObj.optString("type")
            val calculatedRaw = when (trackingType) {
                "numeric" -> {
                    val quantity = dataObj.optDouble("quantity")
                    val unit = dataObj.optString("unit", "")
                    if (unit.isNotBlank()) "$quantity $unit" else quantity.toString()
                }

                "scale" -> {
                    val rating = dataObj.optInt("rating")
                    val maxValue = dataObj.optInt("max_value")
                    "$rating/$maxValue"
                }

                "boolean" -> {
                    val state = dataObj.optBoolean("state")
                    val trueLabel = dataObj.optString("true_label", "Yes")
                    val falseLabel = dataObj.optString("false_label", "No")
                    if (state) trueLabel else falseLabel
                }

                "choice" -> {
                    dataObj.optString("selected_option", "")
                }

                "counter" -> {
                    dataObj.optInt("increment").toString()
                }

                "timer" -> {
                    val seconds = dataObj.optInt("duration_seconds", 0)
                    val h = seconds / 3600
                    val m = (seconds % 3600) / 60
                    val s = seconds % 60
                    buildString {
                        if (h > 0) append("${h}h ")
                        if (m > 0) append("${m}m ")
                        if (s > 0 || (h == 0 && m == 0)) append("${s}s")
                    }.trim()
                }

                "text" -> {
                    dataObj.optString("text", "")
                }

                else -> {
                    // Unknown type: use name as fallback
                    name ?: "[unknown]"
                }
            }

            // Add calculated raw to data
            dataObj.put("raw", calculatedRaw)
            return dataObj.toString()

        } catch (e: Exception) {
            // Log error with context and re-throw to fail the operation
            LogManager.tracking(
                "Failed to enrich tracking data: dataJson=$dataJson, name=$name",
                "ERROR",
                e
            )
            throw e
        }
    }

}