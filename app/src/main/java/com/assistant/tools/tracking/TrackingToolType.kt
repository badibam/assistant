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
import com.assistant.core.fields.CoreFieldUsage
import com.assistant.core.fields.EntryFields
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FixedField
import com.assistant.core.fields.toFieldConfig
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
            "validate_config": false,
            "validate_data": false,
            "always_send": false,
            "display_mode": "LINE"
        }
        """.trimIndent()
    }

    override fun getSchema(schemaId: String, context: Context, toolInstanceId: String?): Schema? {
        // Config schemas (no custom fields)
        if (schemaId.startsWith("tracking_config_")) {
            return when(schemaId) {
                "tracking_config_numeric" -> createConfigNumericSchema(context)
                "tracking_config_scale" -> createConfigScaleSchema(context)
                "tracking_config_boolean" -> createConfigBooleanSchema(context)
                "tracking_config_choice" -> createConfigChoiceSchema(context)
                "tracking_config_counter" -> createConfigCounterSchema(context)
                "tracking_config_timer" -> createConfigTimerSchema(context)
                "tracking_config_text" -> createConfigTextSchema(context)
                else -> null
            }
        }

        // Data schemas, generated from the declared fields: for the tool's own config when it is
        // given, for a new tool of the type the schema id names otherwise
        if (schemaId.startsWith("tracking_data_")) {
            val kind = TrackingKind.entries.firstOrNull { it.dataSchemaId == schemaId } ?: return null
            val config = if (toolInstanceId != null) BaseSchemas.loadToolConfig(toolInstanceId, context)
                         else JSONObject(getDefaultConfig()).put("type", kind.key)
            val s = Strings.`for`(tool = "tracking", context = context)
            return Schema(
                id = schemaId,
                displayName = s.tool("schema_data_display_name"),
                description = s.tool("schema_data_description"),
                category = SchemaCategory.TOOL_DATA,
                content = BaseSchemas.getEntrySchemaOrThrow(this, config, toolInstanceId, context)
            )
        }

        return null
    }

    override fun getAllSchemaIds(): List<String> {
        return listOf(
            "tracking_config_numeric", "tracking_config_scale", "tracking_config_boolean",
            "tracking_config_choice", "tracking_config_counter", "tracking_config_timer", "tracking_config_text",
        ) + TrackingKind.entries.map { it.dataSchemaId }
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
                            },
                            "default_increment": {
                                "type": "integer",
                                "minimum": 1,
                                "default": 1,
                                "description": "${s.tool("schema_config_counter_item_default_increment")}"
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

    /**
     * A tracking entry: a name (the shortcut or the activity), a moment, and a main field,
     * "value", whose field type comes from the tool's type and whose settings come from the
     * config's "value". A numeric one also carries its unit, one of the units the config
     * declares, so an entry keeps saying what it measured when shortcuts change. An occurrence
     * has no value: the entry is the fact that something happened.
     *
     * A timer's value is absent while it runs: its start is in the entry's state until it is
     * stopped.
     */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields {
        val s = Strings.`for`(tool = "tracking", context = context)
        val kind = TrackingKind.of(config)
        val valueType = kind.valueType
            ?: return EntryFields(name = CoreFieldUsage.REQUIRED, timestamp = CoreFieldUsage.OPTIONAL)

        val valueConfig = (config.optJSONObject("value")?.toFieldConfig() ?: emptyMap()).let {
            // A counter counts presses: whole numbers only
            if (kind == TrackingKind.COUNTER) it + ("decimals" to 0) else it
        }
        val fields = mutableListOf(
            FixedField(
                FieldDefinition(
                    name = "value",
                    displayName = s.tool("field_value"),
                    description = s.tool("schema_data_value"),
                    type = valueType,
                    alwaysVisible = true,
                    config = valueConfig.ifEmpty { null }
                ),
                required = kind != TrackingKind.TIMER
            )
        )

        val units = config.optJSONArray("units")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
        if (kind == TrackingKind.NUMERIC && units.isNotEmpty()) {
            fields.add(
                FixedField(
                    FieldDefinition(
                        name = "unit",
                        displayName = s.tool("field_unit"),
                        description = s.tool("schema_data_unit"),
                        type = FieldType.CHOICE,
                        alwaysVisible = true,
                        config = mapOf("options" to units)
                    )
                )
            )
        }

        return EntryFields(name = CoreFieldUsage.REQUIRED, timestamp = CoreFieldUsage.OPTIONAL, data = fields)
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
        return listOf("activity", "trending-up", "scale", "heart-pulse", "dumbbell", "droplet", "moon", "timer")
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
            "default_increment" -> s.tool("field_default_increment")
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
     * Returns config fields relevant for interpreting tracking data entries
     *
     * Includes:
     * - type: determines how to interpret data structure (numeric, scale, choice, etc.)
     * - unit: for numeric values (e.g., "kg", "km")
     * - min/max: for scale values (e.g., 1-10)
     * - min_label/max_label: for scale interpretation (e.g., "Very Bad" to "Excellent")
     * - items: predefined numeric/scale/choice/boolean items
     * - options: available choice options
     * - custom_fields: field definitions (always included)
     *
     * This context allows AI to correctly interpret data values without requesting
     * full config every time.
     */
    override fun getRelevantConfigFieldsForData(): List<String> {
        return listOf("type", "unit", "min", "max", "min_label", "max_label", "items", "options", "extra_fields")
    }

    /**
     * Enrich tracking data by calculating the 'raw' display field
     * The 'raw' field is an auto-generated human-readable representation of the data
     *
     * The schema marks 'raw' system-managed, so the service has already dropped any value a caller
     * sent; the one computed here overwrites the stored one on an update.
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