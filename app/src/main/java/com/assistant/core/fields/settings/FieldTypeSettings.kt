package com.assistant.core.fields.settings

import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.DurationForm
import com.assistant.core.fields.DurationUnit
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.TextLength
import com.assistant.core.themes.TagColor

/**
 * The settings of a field, declared with the fields themselves: what a field definition holds
 * (an element of a config's extra_fields), and what each field type's "config" holds.
 *
 * The schema of a field definition, its checking and what the AI reads of it are generated from
 * here (SettingsSchemaGenerator); no field type's settings are written by hand anywhere else.
 */
object FieldTypeSettings {

    /** The decimals a numeric setting takes: a bound or a step of a field. */
    private const val SETTING_DECIMALS = 2

    /**
     * A field definition: its common settings, then a variant on its type bringing that type's
     * "config".
     *
     * [types] narrows the variant to some types, for the schema of a definition of one type.
     */
    fun definitionNodes(text: (String) -> String, types: List<FieldType> = FieldType.entries): List<SettingNode> = listOf(
        // Made by the app from the label when the field is created, then sent back unchanged to
        // name the field: its format is checked by the service (FieldConfigValidator)
        field("name", "label_name", FieldType.TEXT, text, description = "field_type_schema_name_description",
            config = mapOf("length" to TextLength.SHORT.name)),
        field("display_name", "custom_fields_display_name", FieldType.TEXT, text, required = true,
            description = "field_type_schema_display_name_description", config = mapOf("length" to TextLength.SHORT.name)),
        field("description", "custom_fields_description", FieldType.TEXT, text, config = mapOf("length" to TextLength.MEDIUM.name)),
        field("always_visible", "custom_fields_always_visible", FieldType.BOOLEAN, text, default = false,
            description = "field_type_schema_always_visible_description"),
        SettingNode.Variant(
            selector = field("type", "custom_fields_type", FieldType.CHOICE, text, required = true,
                description = "field_type_schema_type_description",
                config = choice(types.map { it.name }, types.associate { it.name to text("field_type_${it.name.lowercase()}_display_name") })),
            cases = types.associate { type -> type.name to caseNodes(type, text) }
        )
    )

    /** What a field of [type] adds to its definition: its settings, if the type has any. */
    private fun caseNodes(type: FieldType, text: (String) -> String): List<SettingNode> {
        val settings = configNodes(type, text)
        if (settings.isEmpty()) return emptyList()
        // A number or a range needs its decimals, a scale its bounds and a choice its options:
        // their config is required
        return listOf(SettingNode.Group("config", text("field_config_section_title"), settings,
            required = type in setOf(FieldType.NUMERIC, FieldType.RANGE, FieldType.SCALE, FieldType.CHOICE)))
    }

    /** The settings a field of [type] holds in its "config". */
    fun configNodes(type: FieldType, text: (String) -> String): List<SettingNode> = when (type) {
        FieldType.TEXT -> listOf(
            field("length", "field_config_text_length", FieldType.CHOICE, text, default = TextLength.UNLIMITED.name,
                description = "field_type_text_length_description",
                config = choice(TextLength.entries.map { it.name },
                    TextLength.entries.associate { it.name to text("text_length_${it.name.lowercase()}_display_name") }))
        )
        FieldType.NUMERIC -> listOf(
            unit(text), number("min", "field_config_min", text), number("max", "field_config_max", text),
            wholeNumber("decimals", "field_config_decimals", text, default = 0, required = true),
            number("step", "field_config_step", text)
        )
        FieldType.SCALE -> listOf(
            number("min", "field_config_min", text, required = true), number("max", "field_config_max", text, required = true),
            label("min_label", "field_config_min_label", text), label("max_label", "field_config_max_label", text),
            number("step", "field_config_step", text, default = 1)
        )
        FieldType.CHOICE -> listOf(
            SettingNode.ListOf(
                name = "options",
                label = text("field_config_options"),
                item = SettingNode.Item.Of(listOf(
                    field("value", "field_config_option_value", FieldType.TEXT, text, required = true,
                        config = mapOf("length" to TextLength.SHORT.name)),
                    label("label", "field_config_option_label", text),
                    field("color", "field_config_option_color", FieldType.CHOICE, text, config = choice(TagColor.entries.map { it.name }))
                )),
                required = true,
                minItems = 2,
                distinct = true
            ),
            flag("multiple", "field_config_multiple", "field_type_choice_multiple_description", text),
            flag("ordered", "field_config_ordered", "field_type_choice_ordered_description", text),
            flag("open", "field_config_open", "field_type_choice_open_description", text)
        )
        FieldType.BOOLEAN -> listOf(
            label("true_label", "field_config_true_label", text), label("false_label", "field_config_false_label", text)
        )
        FieldType.RANGE -> listOf(
            number("min", "field_config_min", text), number("max", "field_config_max", text), unit(text),
            wholeNumber("decimals", "field_config_decimals", text, default = 0, required = true)
        )
        FieldType.DATE -> emptyList()
        FieldType.TIME -> listOf(clockFormat("format", text))
        FieldType.DATETIME -> listOf(clockFormat("time_format", text))
        FieldType.DURATION -> listOf(
            field("precision", "field_config_duration_precision", FieldType.CHOICE, text,
                default = DurationUnit.DEFAULT_PRECISION.name, description = "field_type_duration_precision_description",
                config = choice(DurationUnit.entries.map { it.name }, DurationUnit.entries.associate { it.name to text("duration_unit_${it.name.lowercase()}_display_name") })),
            field("form", "field_config_duration_form", FieldType.CHOICE, text,
                default = DurationForm.DEFAULT.name, description = "field_type_duration_form_description",
                config = choice(DurationForm.entries.map { it.name }, DurationForm.entries.associate { it.name to text("duration_form_${it.name.lowercase()}_display_name") }))
        )
    }

    private fun field(
        name: String,
        labelKey: String,
        type: FieldType,
        text: (String) -> String,
        required: Boolean = false,
        default: Any? = null,
        description: String? = null,
        config: Map<String, Any>? = null
    ) = SettingNode.Field(
        FieldDefinition(name = name, displayName = text(labelKey), description = description?.let(text),
            type = type, alwaysVisible = false, config = config),
        required = required,
        default = default
    )

    private fun choice(values: List<String>, labels: Map<String, String> = emptyMap()): Map<String, Any> =
        mapOf("options" to ChoiceSettings.storedOptions(values, labels))

    /** A setting that is a number, a bound or a step, with the decimals a setting may need. */
    private fun number(name: String, labelKey: String, text: (String) -> String, required: Boolean = false, default: Any? = null) =
        field(name, labelKey, FieldType.NUMERIC, text, required = required, default = default, config = mapOf("decimals" to SETTING_DECIMALS))

    private fun wholeNumber(name: String, labelKey: String, text: (String) -> String, default: Int, required: Boolean = false) =
        field(name, labelKey, FieldType.NUMERIC, text, required = required, default = default, config = mapOf("min" to 0, "decimals" to 0))

    private fun label(name: String, labelKey: String, text: (String) -> String) =
        field(name, labelKey, FieldType.TEXT, text, config = mapOf("length" to TextLength.SHORT.name))

    private fun unit(text: (String) -> String) = label("unit", "field_config_unit", text)

    private fun flag(name: String, labelKey: String, descriptionKey: String, text: (String) -> String) =
        field(name, labelKey, FieldType.BOOLEAN, text, default = false, description = descriptionKey)

    private fun clockFormat(name: String, text: (String) -> String) =
        field(name, "field_config_time_format", FieldType.CHOICE, text, default = "24h",
            config = choice(listOf("24h", "12h")))
}
