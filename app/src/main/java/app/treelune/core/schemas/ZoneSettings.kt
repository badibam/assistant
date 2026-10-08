package app.treelune.core.schemas

import android.content.Context
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.TextLength
import app.treelune.core.fields.settings.SettingNode
import app.treelune.core.fields.settings.SettingsSchemaGenerator
import app.treelune.core.strings.Strings
import app.treelune.core.validation.Schema
import app.treelune.core.validation.SchemaCategory

/**
 * A zone's settings, declared with the fields (docs/DATA.md): what a zone stores
 * beside its identity and its place in the list. Its schema, "zone_config", is generated from here,
 * and ZoneService checks every write against it.
 */
object ZoneSettings {

    const val SCHEMA_ID = "zone_config"

    private val MODES = app.treelune.core.grid.ZonePositions.MODES.map { it.name }

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
            // A tag's name, or none for a neutral icon; checked with its names by the service
            SettingNode.Field(
                FieldDefinition(app.treelune.core.themes.IconColor.KEY, s.shared("label_icon_color"), s.shared("zone_schema_icon_color"), FieldType.CHOICE, false,
                    mapOf("options" to app.treelune.core.themes.IconColor.options(s::shared)))
            ),
            // One of the zone groups of the main screen
            field("group", "label_zone_group", FieldType.TEXT, TextLength.SHORT),
            // How its tile shows on the main screen, which gives the cells it takes
            SettingNode.Field(
                FieldDefinition("display_mode", s.shared("tools_config_label_display_mode"), s.shared("zone_schema_display_mode"), FieldType.CHOICE, false,
                    mapOf("options" to app.treelune.core.fields.ChoiceSettings.storedOptions(MODES, MODES.associateWith { s.shared("tools_config_display_${it.lowercase()}") }))),
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
