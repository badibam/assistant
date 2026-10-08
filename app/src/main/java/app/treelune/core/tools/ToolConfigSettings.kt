package app.treelune.core.tools

import android.content.Context
import app.treelune.core.fields.ChoiceSettings
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.FieldTypeSettings
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory

/**
 * The config of a tool instance, declared with the fields: the settings every tool has, then
 * the ones its tool type declares (ToolTypeContract.getConfigSettings), side by side in one
 * object. Its schema is generated from here, and the service checks every config write against it.
 */
object ToolConfigSettings {

    /** Display modes a tool can be shown in, in a zone. */
    private val DISPLAY_MODES = listOf("ICON", "MINIMAL", "LINE", "CONDENSED", "EXTENDED", "SQUARE", "FULL")

    /** The general settings every tool has, whatever its type. */
    private fun generalNodes(toolType: ToolTypeContract, context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        val text: (String) -> String = s::shared
        return listOf(
            SettingNode.Section(text("tools_config_section_general_params"), listOf(
                field("name", text("tools_config_label_name"), FieldType.TEXT, text("tools_base_schema_config_name"),
                    required = true, config = mapOf("length" to TextLength.SHORT.name)),
                field("description", text("tools_config_label_description"), FieldType.TEXT, text("tools_base_schema_config_description"),
                    config = mapOf("length" to TextLength.MEDIUM.name)),
                // Checked against the icon index by the service (ToolInstanceService)
                field("icon_name", text("tools_config_label_icon"), FieldType.TEXT, text("tools_base_schema_config_icon_name"),
                    default = toolType.getDefaultIconName(), config = mapOf("length" to TextLength.SHORT.name)),
                // A tag's name, or none for a neutral icon; checked with its names by the service
                field(app.treelune.core.themes.IconColor.KEY, text("label_icon_color"), FieldType.CHOICE, text("tools_base_schema_config_icon_color"),
                    config = mapOf("options" to app.treelune.core.themes.IconColor.options(text))),
                field("management", text("tools_config_label_management"), FieldType.CHOICE, text("tools_base_schema_config_management"),
                    default = "manual",
                    config = choice(listOf("manual", "ai"), mapOf("manual" to text("tools_config_option_manual"), "ai" to text("tools_config_option_ai")))),
                field("display_mode", text("tools_config_label_display_mode"), FieldType.CHOICE, text("tools_base_schema_config_display_mode"),
                    default = toolType.getDefaultDisplayMode(),
                    config = choice(DISPLAY_MODES, DISPLAY_MODES.associateWith { text("tools_config_display_${it.lowercase()}") })),
                field("group", text("label_group"), FieldType.TEXT, text("tools_base_schema_config_group"),
                    config = mapOf("length" to TextLength.SHORT.name)),
                field("validate_config", text("tools_config_label_config_validation"), FieldType.BOOLEAN, text("tools_base_schema_config_validate_config"), default = false),
                field("validate_data", text("tools_config_label_data_validation"), FieldType.BOOLEAN, text("tools_base_schema_config_validate_data"), default = false),
                field("always_send", text("tools_config_label_always_send"), FieldType.BOOLEAN, text("tools_base_schema_config_always_send"), default = false)
            ))
        )
    }

    /**
     * The user's own fields, which every tool can add to its entries, and whether their names
     * show beside their values wherever they are shown (CustomFieldsDisplay).
     */
    private fun extraFieldsNodes(toolType: ToolTypeContract, context: Context): List<SettingNode> {
        val text: (String) -> String = Strings.`for`(context = context)::shared
        return listOf(
            field(SHOW_FIELD_LABELS, text("tools_config_label_show_field_labels"), FieldType.BOOLEAN,
                text("tools_base_schema_config_show_field_labels"), default = toolType.getDefaultShowFieldLabels()),
            SettingNode.ListOf("extra_fields", text("custom_fields_section_title"),
                SettingNode.Item.Of(FieldTypeSettings.definitionNodes(text)), fieldDefinitions = true,
                summary = listOf("display_name", "type"))
        )
    }

    /** The setting that says whether the user's fields show their names. */
    const val SHOW_FIELD_LABELS = "show_field_labels"

    /**
     * The whole config of a tool of [toolType]: the general settings, its type's, then the user's
     * own fields, which come after the entries' main field they add to — for a tool that keeps
     * entries only.
     */
    fun nodes(toolType: ToolTypeContract, context: Context): List<SettingNode> =
        // Built once per tool type and language: it depends on nothing else, and a tile reads
        // its name and icon through it on every drawing (a chart's takes some 150 ms to build)
        declarations.getOrPut(toolType to context.resources.configuration.locales[0]) {
            generalNodes(toolType, context) + toolType.getConfigSettings(context) +
                (if (toolType.keepsEntries()) extraFieldsNodes(toolType, context) else emptyList())
        }

    private val declarations = java.util.concurrent.ConcurrentHashMap<Pair<ToolTypeContract, java.util.Locale>, List<SettingNode>>()

    /** What a new tool of [toolType] starts from: its declared defaults (SettingDefaults). */
    fun defaults(toolType: ToolTypeContract, context: Context): org.json.JSONObject =
        app.treelune.core.fields.settings.SettingDefaults.of(nodes(toolType, context))

    /**
     * [config] with every setting of the first level it leaves out at its declared default: a
     * default is also what a setting's absence means. Settings under a variant are left as
     * they are, since which ones apply depends on the option the config chose.
     */
    fun withDefaults(toolType: ToolTypeContract, config: org.json.JSONObject, context: Context): org.json.JSONObject {
        val filled = org.json.JSONObject(config.toString())
        app.treelune.core.fields.settings.SettingsSchemaGenerator.flatten(nodes(toolType, context))
            .filterIsInstance<SettingNode.Field>()
            .forEach { node -> node.default?.let { if (!filled.has(node.definition.name)) filled.put(node.definition.name, it) } }
        return filled
    }

    /** [config] of a tool of [toolType], read through its declaration (SettingValues). */
    fun read(toolType: ToolTypeContract, config: org.json.JSONObject, context: Context): app.treelune.core.fields.settings.SettingValues =
        app.treelune.core.fields.settings.SettingValues(nodes(toolType, context), config)

    /** [config] of a tool of [tooltype], read through its declaration. */
    fun read(tooltype: String, config: org.json.JSONObject, context: Context): app.treelune.core.fields.settings.SettingValues =
        read(ToolTypeManager.getToolType(tooltype) ?: error("Unknown tooltype $tooltype"), config, context)

    /** The schema a config of [toolType] is held to, under [id]. */
    fun schema(toolType: ToolTypeContract, id: String, context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = id,
            displayName = toolType.getDisplayName(context),
            description = toolType.getDescription(context),
            category = SchemaCategory.TOOL_CONFIG,
            content = SettingsSchemaGenerator.generate(nodes(toolType, context), s::shared).toString()
        )
    }

    private fun field(name: String, label: String, type: FieldType, description: String?, required: Boolean = false,
                      default: Any? = null, config: Map<String, Any>? = null) =
        SettingNode.Field(FieldDefinition(name, label, description, type, false, config), required = required, default = default)

    private fun choice(values: List<String>, labels: Map<String, String>): Map<String, Any> =
        mapOf("options" to ChoiceSettings.storedOptions(values, labels))
}
