package com.assistant.core.schemas

import android.content.Context
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.fields.settings.SettingsSchemaGenerator
import com.assistant.core.strings.Strings
import com.assistant.core.validation.Schema
import com.assistant.core.validation.SchemaCategory

/**
 * A zone's settings, declared with the fields (docs/DATA.md): what a zone stores
 * beside its identity and its place in the list. Its schema, "zone_config", is generated from here,
 * and ZoneService checks every write against it.
 */
object ZoneSettings {

    const val SCHEMA_ID = "zone_config"

    private val MODES = com.assistant.core.grid.ZonePositions.MODES.map { it.name }

    /** Where the icon picker starts for a zone: the themes zones are usually made of. */
    val SUGGESTED_ICONS = listOf(
        "heart", "dumbbell", "briefcase", "house", "wallet", "graduation-cap",
        "users", "leaf", "book-open", "utensils", "plane", "music", "palette", "folder"
    )

    fun nodes(context: Context): List<SettingNode> {
        val s = Strings.`for`(context = context)
        fun field(name: String, labelKey: String, type: FieldType, length: TextLength, required: Boolean = false) =
            SettingNode.Field(
                FieldDefinition(name, s.shared(labelKey), s.shared("zone_schema_$name"), type, false, mapOf("length" to length.name)),
                required = required
            )
        return listOf(
            field("name", "label_zone_name", FieldType.TEXT, TextLength.SHORT, required = true),
            field("description", "label_description", FieldType.TEXT, TextLength.MEDIUM),
            // Checked against the icon index by the service
            field("icon_name", "label_icon", FieldType.TEXT, TextLength.SHORT),
            // One of the zone groups of the main screen
            field("group", "label_zone_group", FieldType.TEXT, TextLength.SHORT),
            // How its tile shows on the main screen, which gives the cells it takes
            SettingNode.Field(
                FieldDefinition("display_mode", s.shared("tools_config_label_display_mode"), s.shared("zone_schema_display_mode"), FieldType.CHOICE, false,
                    mapOf("options" to com.assistant.core.fields.ChoiceSettings.storedOptions(MODES, MODES.associateWith { s.shared("tools_config_display_${it.lowercase()}") }))),
                required = true, default = "LINE"
            ),
            // The groups the zone's tools are sorted into
            SettingNode.ListOf("tool_groups", s.shared("label_tool_groups"),
                SettingNode.Item.Value(FieldDefinition("tool_group", s.shared("label_tool_group"), s.shared("zone_schema_tool_groups"),
                    FieldType.TEXT, false, mapOf("length" to TextLength.SHORT.name))),
                distinct = true)
        )
    }

    fun schema(context: Context): Schema {
        val s = Strings.`for`(context = context)
        return Schema(
            id = SCHEMA_ID,
            displayName = s.shared("zone_config_schema_display_name"),
            description = s.shared("zone_config_schema_description"),
            category = SchemaCategory.ZONE_CONFIG,
            content = SettingsSchemaGenerator.generate(nodes(context), s::shared).toString()
        )
    }
}
