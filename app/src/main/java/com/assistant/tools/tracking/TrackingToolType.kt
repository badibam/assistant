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
            val kind = TrackingKind.entries.firstOrNull { it.configSchemaId == schemaId } ?: return null
            return createConfigSchema(kind, context)
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
        return TrackingKind.entries.map { it.configSchemaId } + TrackingKind.entries.map { it.dataSchemaId }
    }

    /**
     * The config schema of a tracking tool of [kind], on top of the base config:
     * - value: the settings of the entries' main field, held to the config schema of its field
     *   type (FieldTypeSchemaProvider), so they are checked like any field's settings;
     * - units: the units a numeric value can be in;
     * - items: the shortcuts, each a name, and for a numeric or counter tool the value it enters
     *   (and for a numeric one its unit, one of the units);
     * - allow_decrement: whether a counter's shortcuts also take away.
     */
    private fun createConfigSchema(kind: TrackingKind, context: Context): Schema {
        val s = Strings.`for`(tool = "tracking", context = context)
        val properties = JSONObject()
            .put("type", JSONObject().put("type", "string").put("const", kind.key).put("description", s.tool("schema_config_type")))

        kind.valueType?.let { valueType ->
            val fieldTypeSchema = com.assistant.core.fields.FieldTypeSchemaProvider.getSchema("field_type_${valueType.name}", context, null)
                ?: throw IllegalStateException("No schema for field type ${valueType.name}")
            val valueSchema = JSONObject(fieldTypeSchema.content).getJSONObject("properties").getJSONObject("config")
            properties.put("value", valueSchema.put("description", s.tool("schema_config_value")))
        }

        val item = JSONObject()
            .put("name", JSONObject().put("type", "string").put("minLength", 1).put("maxLength", FieldLimits.SHORT_LENGTH)
                .put("description", s.tool("schema_config_item_name")))
        if (kind == TrackingKind.NUMERIC || kind == TrackingKind.COUNTER) {
            item.put("value", JSONObject().put("type", "number").put("description", s.tool("schema_config_item_value")))
        }
        if (kind == TrackingKind.NUMERIC) {
            item.put("unit", JSONObject().put("type", "string").put("description", s.tool("schema_config_item_unit")))
            properties.put("units", JSONObject().put("type", "array").put("uniqueItems", true)
                .put("items", JSONObject().put("type", "string").put("minLength", 1).put("maxLength", FieldLimits.SHORT_LENGTH))
                .put("description", s.tool("schema_config_units")))
        }
        if (kind == TrackingKind.COUNTER) {
            properties.put("allow_decrement", JSONObject().put("type", "boolean").put("default", true)
                .put("description", s.tool("schema_config_counter_allow_decrement")))
        }
        properties.put("items", JSONObject().put("type", "array").put("description", s.tool("schema_config_items"))
            .put("items", JSONObject().put("type", "object").put("properties", item)
                .put("required", org.json.JSONArray().put("name")).put("additionalProperties", false)))

        // A scale needs its bounds and a choice its options: their value settings are required
        val required = org.json.JSONArray().put("type")
        if (kind == TrackingKind.SCALE || kind == TrackingKind.CHOICE) required.put("value")

        return Schema(
            id = kind.configSchemaId,
            displayName = s.tool("schema_config_${kind.key}_display_name"),
            description = s.tool("schema_config_${kind.key}_description"),
            category = SchemaCategory.TOOL_CONFIG,
            content = BaseSchemas.createExtendedSchema(
                BaseSchemas.getBaseConfigSchema(context),
                JSONObject().put("properties", properties).put("required", required).toString()
            )
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
            "type" -> s.tool("field_type")
            "value" -> s.tool("field_value")
            "unit" -> s.tool("field_unit")
            "units" -> s.tool("field_units")
            "items" -> s.tool("field_items")
            "allow_decrement" -> s.tool("field_allow_decrement")
            else -> s.tool("field_unknown")
        }
    }

    /**
     * The config keys that say how to read a tracking entry: the kind of value (type), its
     * settings (value: a scale's bounds, a choice's options...), the units a numeric value can
     * be in, the shortcuts, and the user's fields.
     */
    override fun settleEntries(entries: List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> =
        TrackingStopwatch.settle(entries, writtenId)
}
