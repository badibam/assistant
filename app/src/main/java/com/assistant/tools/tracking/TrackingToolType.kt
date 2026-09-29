package com.assistant.tools.tracking

import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.FieldTypeSettings
import com.assistant.core.fields.settings.SettingNode
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
object TrackingToolType : ToolTypeContract {
    
    override fun getDisplayName(context: Context): String {
        val s = Strings.`for`(tool = "tracking", context = context)
        return s.tool("display_name")
    }

    override fun getDescription(context: Context): String {
        val s = Strings.`for`(tool = "tracking", context = context)
        return s.tool("description")
    }

    override fun getDefaultDisplayMode(): String = "LINE"


    override fun getDefaultShowFieldLabels(): Boolean = true

    /**
     * A tracking tool's settings: what it follows ("type"), and for that type
     * - value: the settings of the entries' main field, those of its field type (FieldTypeSettings),
     *   and for a counter the unit it counts in;
     * - units: the units a numeric value can be in;
     * - items: the shortcuts, each a name, and for a numeric or counter tool the value it enters
     *   (and for a numeric one its unit, one of the units);
     * - allow_decrement: whether a counter's shortcuts also take away.
     */
    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = Strings.`for`(tool = "tracking", context = context)
        val shared = Strings.`for`(context = context)
        fun field(name: String, label: String, description: String, type: FieldType, required: Boolean = false,
                  default: Any? = null, config: Map<String, Any>? = null) =
            SettingNode.Field(FieldDefinition(name, label, description, type, false, config), required = required, default = default)
        val short = mapOf("length" to TextLength.SHORT.name)
        val kinds = TrackingKind.entries.map { it.key }

        return listOf(SettingNode.Variant(
            selector = field("type", s.tool("config_label_tracking_type"), s.tool("schema_config_type"), FieldType.CHOICE,
                default = TrackingKind.NUMERIC.key, config = mapOf("options" to ChoiceSettings.storedOptions(kinds, kinds.associateWith { s.tool("config_option_$it") }))),
            cases = TrackingKind.entries.associate { kind ->
                val item = listOfNotNull(
                    field("name", shared.shared("label_name"), s.tool("schema_config_item_name"), FieldType.TEXT, required = true, config = short),
                    if (kind == TrackingKind.NUMERIC || kind == TrackingKind.COUNTER)
                        field("value", s.tool("field_value"), s.tool("schema_config_item_value"), FieldType.NUMERIC, config = mapOf("decimals" to 2))
                    else null,
                    if (kind == TrackingKind.NUMERIC)
                        field("unit", s.tool("field_unit"), s.tool("schema_config_item_unit"), FieldType.TEXT, config = short)
                    else null
                )
                kind.key to listOfNotNull(
                    // A number needs its decimals, a scale its bounds and a choice its options:
                    // their value settings are required. A counter counts one thing, named by its
                    // unit; a numeric entry takes its own unit from "units".
                    kind.valueType?.let { valueType ->
                        SettingNode.Group("value", s.tool("schema_config_value"),
                            listOfNotNull(if (kind == TrackingKind.COUNTER) FieldTypeSettings.unit(shared::shared) else null) +
                                FieldTypeSettings.configNodes(valueType, shared::shared),
                            required = kind == TrackingKind.NUMERIC || kind == TrackingKind.SCALE || kind == TrackingKind.CHOICE)
                    },
                    if (kind == TrackingKind.NUMERIC)
                        SettingNode.ListOf("units", s.tool("field_units"),
                            SettingNode.Item.Value(FieldDefinition("unit", s.tool("field_unit"), s.tool("schema_config_units"), FieldType.TEXT, false, short)),
                            distinct = true)
                    else null,
                    if (kind == TrackingKind.COUNTER)
                        field("allow_decrement", s.tool("field_allow_decrement"), s.tool("schema_config_counter_allow_decrement"),
                            FieldType.BOOLEAN, default = true)
                    else null,
                    SettingNode.ListOf("items", s.tool("field_items"), SettingNode.Item.Of(item),
                        summary = item.map { it.definition.name })
                )
            }
        ))
    }

    /**
     * A tracking entry: a name (the shortcut or the activity), a moment, and a main field,
     * "value", whose field type comes from the tool's type and whose settings come from the
     * config's "value". A numeric one also carries its unit, one of the units the config
     * declares or a new one that joins them, so an entry keeps saying what it measured when
     * shortcuts change. An occurrence
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

        // Open: a unit given with an entry joins the units (configWithOptionsAdded), so a tool
        // without units yet takes its first one from an entry
        if (kind == TrackingKind.NUMERIC) {
            fields.add(
                FixedField(
                    FieldDefinition(
                        name = "unit",
                        displayName = s.tool("field_unit"),
                        description = s.tool("schema_data_unit"),
                        type = FieldType.CHOICE,
                        alwaysVisible = true,
                        config = mapOf("options" to ChoiceSettings.storedOptions(TrackingConfig.units(config)), "open" to true)
                    )
                )
            )
        }

        return EntryFields(name = CoreFieldUsage.REQUIRED, timestamp = CoreFieldUsage.OPTIONAL, data = fields)
    }

    /** A new unit given with a numeric entry joins the end of the units. */
    override fun configWithOptionsAdded(config: JSONObject, field: String, added: List<String>): JSONObject {
        check(field == "unit") { "tracking declares no open choice \"$field\"" }
        return JSONObject(config.toString()).put("units", org.json.JSONArray(TrackingConfig.units(config) + added))
    }

    override fun getDefaultIconName(): String {
        return "activity"
    }
    
    override fun getSuggestedIcons(): List<String> {
        return listOf("activity", "trending-up", "scale", "heart-pulse", "dumbbell", "droplet", "moon", "timer")
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
        onLongClick: () -> Unit,
        openEntry: com.assistant.core.tools.EntryToOpen?
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
    @Composable
    override fun rememberTile(tool: com.assistant.core.database.entities.ToolInstance, open: (com.assistant.core.tools.EntryToOpen) -> Unit): com.assistant.core.tools.ToolTile =
        com.assistant.tools.tracking.ui.rememberTrackingTile(tool)

    override fun settleEntries(entries: List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> =
        TrackingStopwatch.settle(entries, writtenId)
}
