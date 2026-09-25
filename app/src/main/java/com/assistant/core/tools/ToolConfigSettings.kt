package com.assistant.core.tools

import android.content.Context
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.FieldTypeSettings
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.fields.settings.SettingsSchemaGenerator
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory

/**
 * The config of a tool instance, declared with the fields: the settings every tool has, then
 * the ones its tool type declares (ToolTypeContract.getConfigSettings), side by side in one
 * object. Its schema is generated from here, and the service checks every config write against it.
 */
object ToolConfigSettings {

    /** Display modes a tool can be shown in, in a zone. */
    private val DISPLAY_MODES = listOf("ICON", "MINIMAL", "LINE", "CONDENSED", "EXTENDED", "SQUARE", "FULL")

    /** The settings every tool has, whatever its type. */
    fun commonNodes(toolType: ToolTypeContract, context: Context): List<SettingNode> {
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
                field("management", text("tools_config_label_management"), FieldType.CHOICE, text("tools_base_schema_config_management"),
                    required = true, default = "manual",
                    config = choice(listOf("manual", "ai"), mapOf("manual" to text("tools_config_option_manual"), "ai" to text("tools_config_option_ai")))),
                field("display_mode", text("tools_config_label_display_mode"), FieldType.CHOICE, text("tools_base_schema_config_display_mode"),
                    required = true, default = toolType.getDefaultDisplayMode(),
                    config = choice(DISPLAY_MODES, DISPLAY_MODES.associateWith { text("tools_config_display_${it.lowercase()}") })),
                field("group", text("label_group"), FieldType.TEXT, text("tools_base_schema_config_group"),
                    config = mapOf("length" to TextLength.SHORT.name)),
                field("validate_config", text("tools_config_label_config_validation"), FieldType.BOOLEAN, text("tools_base_schema_config_validate_config"), default = false),
                field("validate_data", text("tools_config_label_data_validation"), FieldType.BOOLEAN, text("tools_base_schema_config_validate_data"), default = false),
                field("always_send", text("tools_config_label_always_send"), FieldType.BOOLEAN, text("tools_base_schema_config_always_send"), default = false)
            )),
            SettingNode.ListOf("extra_fields", text("custom_fields_section_title"),
                SettingNode.Item.Of(FieldTypeSettings.definitionNodes(text)), fieldDefinitions = true)
        )
    }

    /** The whole config of a tool of [toolType]: the common settings, then its type's. */
    fun nodes(toolType: ToolTypeContract, context: Context): List<SettingNode> =
        commonNodes(toolType, context) + toolType.getConfigSettings(context)

    /** What a new tool of [toolType] starts from: its declared defaults (SettingDefaults). */
    fun defaults(toolType: ToolTypeContract, context: Context): org.json.JSONObject =
        com.assistant.core.fields.settings.SettingDefaults.of(nodes(toolType, context))

    /** [config] of a tool of [toolType], read through its declaration (SettingValues). */
    fun read(toolType: ToolTypeContract, config: org.json.JSONObject, context: Context): com.assistant.core.fields.settings.SettingValues =
        com.assistant.core.fields.settings.SettingValues(nodes(toolType, context), config)

    /** [config] of a tool of [tooltype], read through its declaration. */
    fun read(tooltype: String, config: org.json.JSONObject, context: Context): com.assistant.core.fields.settings.SettingValues =
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
