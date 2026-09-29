package com.assistant.tools.chart

import android.content.Context
import androidx.compose.runtime.Composable
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.CoreFieldUsage
import com.assistant.core.fields.EntryFields
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.TextLength
import com.assistant.core.fields.settings.RowFields
import com.assistant.core.fields.settings.SettingNode
import com.assistant.core.fields.settings.labelOf
import com.assistant.core.services.ExecutableService
import com.assistant.core.strings.Strings
import com.assistant.core.terms.Term
import com.assistant.core.themes.TagColor
import com.assistant.core.tools.BaseSchemas
import com.assistant.core.tools.ToolTypeContract
import com.assistant.tools.chart.ui.ChartScreen
import org.json.JSONObject

/**
 * A chart (docs/design/missing-tools.md, « Graphique »): a view of other tools' entries and of
 * variables, which draws and computes nothing. Its config is a subset of Vega-Lite, the AI's
 * own grammar, declared setting by setting so that its form and schema are generated like any
 * other: a displayed period, layers each reading one source — a tool's entries, or a grid of
 * readings and variables at each step of the period — into a table of named columns, and marks
 * whose channels name those columns, laid out here (ChartSceneBuilder) into a drawing the theme draws.
 *
 * It keeps no entries: a chart only shows those of others.
 */
object ChartToolType : ToolTypeContract {

    private fun s(context: Context) = Strings.`for`(tool = "chart", context = context)

    override fun getDisplayName(context: Context): String = s(context).tool("display_name")
    override fun getDescription(context: Context): String = s(context).tool("description")
    override fun getDefaultDisplayMode(): String = "LINE"
    override fun getDefaultShowFieldLabels(): Boolean = true
    override fun getDefaultIconName(): String = "chart-line"
    override fun getSuggestedIcons(): List<String> = listOf("chart-line", "chart-column", "chart-pie", "chart-area", "chart-scatter", "trending-up")

    override fun getFormFieldName(fieldName: String, context: Context): String =
        getConfigSettings(context).labelOf(fieldName) ?: BaseSchemas.getCommonFieldName(fieldName, context) ?: fieldName

    override fun keepsEntries(): Boolean = false

    /** No entry: neither name nor date nor field. */
    override fun getEntryFields(config: JSONObject, context: Context): EntryFields =
        EntryFields(name = CoreFieldUsage.ABSENT, timestamp = CoreFieldUsage.ABSENT)

    // -----------------------------------------------------------------------------------------
    // The declaration
    // -----------------------------------------------------------------------------------------

    private fun field(name: String, label: String, type: FieldType, description: String? = null, config: Map<String, Any>? = null) =
        FieldDefinition(name, label, description, type, false, config)

    private fun choice(values: List<String>, labels: (String) -> String): Map<String, Any> =
        mapOf("options" to ChoiceSettings.storedOptions(values, values.associateWith(labels), emptyMap()))

    /** A palette name, chosen among the palette's colors shown as themselves. */
    private fun colorChoice(name: String, label: String, description: String?, context: Context): FieldDefinition {
        val shared = Strings.`for`(context = context)
        val names = TagColor.entries.map { it.name }
        return field(name, label, FieldType.CHOICE, description, mapOf("options" to ChoiceSettings.storedOptions(
            names, names.associateWith { shared.shared("tag_color_${it.lowercase()}") }, TagColor.entries.associate { it.name to it })))
    }

    override fun getConfigSettings(context: Context): List<SettingNode> {
        val s = s(context)
        return listOf(
            SettingNode.Section(s.tool("section_chart"), listOf(
                SettingNode.Period(ChartKeys.PERIOD, s.tool("field_period"), s.tool("reference_display"), required = true),
                SettingNode.Variant(
                    selector = SettingNode.Field(field(ChartKeys.COMPOSITION, s.tool("field_composition"), FieldType.CHOICE, s.tool("schema_composition"),
                        choice(ChartKeys.COMPOSITIONS) { s.tool("composition_$it") }), required = true, default = ChartKeys.SINGLE),
                    cases = mapOf(
                        ChartKeys.SINGLE to listOf(layers(context)),
                        ChartKeys.FACET to listOf(
                            SettingNode.Group(ChartKeys.FACET, s.tool("field_facet"), listOf(
                                SettingNode.Field(field(ChartKeys.FIELD, s.tool("field_facet_field"), FieldType.TEXT, s.tool("schema_facet_field"), short()), required = true, rowField = true)
                            ), required = true),
                            columns(context),
                            layers(context)
                        ),
                        ChartKeys.REPEAT to listOf(
                            SettingNode.ListOf(ChartKeys.REPEAT, s.tool("field_repeat"),
                                SettingNode.Item.Value(field(ChartKeys.REPEAT, s.tool("field_repeat_field"), FieldType.TEXT, s.tool("schema_repeat"), short()), rowField = true),
                                required = true, minItems = 1),
                            columns(context),
                            layers(context)
                        )
                    ) + ConcatDirection.entries.associate { direction ->
                        direction.key to listOfNotNull(
                            SettingNode.ListOf(direction.key, s.tool("field_views"), SettingNode.Item.Of(listOf(
                                SettingNode.Field(field(ChartKeys.TITLE, s.tool("field_view_title"), FieldType.TEXT, s.tool("schema_view_title"), short())),
                                layers(context)
                            )), required = true, minItems = 1, summary = listOf(ChartKeys.TITLE)),
                            columns(context).takeIf { direction == ConcatDirection.WRAP }
                        )
                    }
                )
            ))
        )
    }

    private fun short() = mapOf("length" to TextLength.SHORT.name)

    /** How many small views stand side by side before the next row. */
    private fun columns(context: Context) = SettingNode.Field(field(ChartKeys.COLUMNS, s(context).tool("field_columns"), FieldType.NUMERIC,
        s(context).tool("schema_columns"), mapOf("min" to 1, "max" to 4, "decimals" to 0)))

    /** The layers of a view, drawn over one another. */
    private fun layers(context: Context): SettingNode.ListOf {
        val s = s(context)
        return SettingNode.ListOf(ChartKeys.LAYER, s.tool("field_layers"), SettingNode.Item.Of(layerNodes(context)),
            required = true, minItems = 1, summary = listOf(ChartKeys.DESCRIPTION, ChartKeys.SOURCE))
    }

    private fun layerNodes(context: Context): List<SettingNode> {
        val s = s(context)
        return listOf(
            SettingNode.Field(field(ChartKeys.DESCRIPTION, s.tool("field_layer_description"), FieldType.TEXT, s.tool("schema_layer_description"), short())),
            SettingNode.Variant(
                selector = SettingNode.Field(field(ChartKeys.SOURCE, s.tool("field_source"), FieldType.CHOICE, s.tool("schema_source"),
                    choice(listOf(ChartKeys.GRID, ChartKeys.ENTRIES)) { s.tool("source_$it") }), required = true, default = ChartKeys.GRID),
                cases = mapOf(
                    ChartKeys.ENTRIES to listOf(
                        SettingNode.Selection(ChartKeys.SELECTION, s.tool("field_selection"), s.tool("reference_display"), required = true)
                    ),
                    ChartKeys.GRID to listOf(
                        SettingNode.Field(field(ChartKeys.STEP, s.tool("field_step"), FieldType.CHOICE, s.tool("schema_step"),
                            choice(ChartKeys.STEPS.keys.toList()) { s.tool("step_$it") }), required = true, default = "day"),
                        SettingNode.ListOf(ChartKeys.COLUMNS, s.tool("field_grid_columns"), SettingNode.Item.Of(listOf(
                            SettingNode.Field(field(ChartKeys.NAME, s.tool("field_column_name"), FieldType.TEXT, s.tool("schema_column_name"), short()), required = true),
                            SettingNode.Term(ChartKeys.TERM, s.tool("field_column_term"), setOf(Term.Kind.VARIABLE, Term.Kind.READING),
                                s.tool("reference_step"), s.tool("empty_period_step"), required = true)
                        )), required = true, minItems = 1, summary = listOf(ChartKeys.NAME))
                    )
                )
            ),
            SettingNode.ListOf(ChartKeys.TRANSFORM, s.tool("field_transform"), SettingNode.Item.Of(listOf(
                SettingNode.ListOf(ChartKeys.FOLD, s.tool("field_fold"), SettingNode.Item.Value(field(ChartKeys.FOLD, s.tool("field_column"), FieldType.TEXT, s.tool("schema_fold"), short()), rowField = true)),
                SettingNode.ListOf(ChartKeys.AS, s.tool("field_as"), SettingNode.Item.Value(field(ChartKeys.AS, s.tool("field_column_name"), FieldType.TEXT, s.tool("schema_as"), short()))),
                SettingNode.ListOf(ChartKeys.FLATTEN, s.tool("field_flatten"), SettingNode.Item.Value(field(ChartKeys.FLATTEN, s.tool("field_column"), FieldType.TEXT, s.tool("schema_flatten"), short()), rowField = true))
            )), summary = listOf(ChartKeys.FOLD, ChartKeys.FLATTEN)),
            SettingNode.Group(ChartKeys.MARK, s.tool("field_mark"), listOf(markVariant(context)), required = true),
            SettingNode.Group(ChartKeys.ENCODING, s.tool("field_encoding"), listOf(
                channel(Channel.X, context), channel(Channel.Y, context), channel(Channel.COLOR, context),
                SettingNode.Section(s.tool("section_more_channels"), listOf(
                    Channel.X2, Channel.Y2, Channel.SIZE, Channel.SHAPE, Channel.OPACITY, Channel.STROKE_DASH,
                    Channel.DETAIL, Channel.ORDER, Channel.TEXT, Channel.THETA, Channel.RADIUS
                ).map { channel(it, context) })
            ))
        )
    }

    /** The mark's type, and the styles each type takes. */
    private fun markVariant(context: Context): SettingNode.Variant {
        val s = s(context)
        val interpolate = SettingNode.Field(field(ChartKeys.INTERPOLATE, s.tool("field_interpolate"), FieldType.CHOICE, s.tool("schema_interpolate"),
            choice(Interpolate.entries.map { it.key }) { s.tool("interpolate_${it.replace('-', '_')}") }))
        val opacity = SettingNode.Field(field(ChartKeys.OPACITY, s.tool("field_opacity"), FieldType.NUMERIC, s.tool("schema_opacity"), mapOf("min" to 0, "max" to 1, "decimals" to 2)))
        val strokeWidth = SettingNode.Field(field(ChartKeys.STROKE_WIDTH, s.tool("field_stroke_width"), FieldType.NUMERIC, s.tool("schema_stroke_width"), mapOf("min" to 0.5, "max" to 12, "decimals" to 1)))
        val strokeDash = SettingNode.ListOf(ChartKeys.STROKE_DASH, s.tool("field_stroke_dash"),
            SettingNode.Item.Value(field(ChartKeys.STROKE_DASH, s.tool("field_dash_length"), FieldType.NUMERIC, s.tool("schema_stroke_dash"), mapOf("min" to 1, "max" to 40, "decimals" to 0))))
        val shape = SettingNode.Field(field(ChartKeys.SHAPE, s.tool("field_shape"), FieldType.CHOICE, s.tool("schema_shape"), choice(Shape.entries.map { it.key }) { s.tool("shape_$it") }))
        return SettingNode.Variant(
            selector = SettingNode.Field(field(ChartKeys.TYPE, s.tool("field_mark_type"), FieldType.CHOICE, s.tool("schema_mark_type"),
                choice(MarkType.entries.map { it.key }) { s.tool("mark_$it") }), required = true, default = MarkType.LINE.key),
            cases = mapOf(
                MarkType.LINE.key to listOf(interpolate, SettingNode.Field(field(ChartKeys.POINT, s.tool("field_point"), FieldType.BOOLEAN, s.tool("schema_point"))), strokeDash, strokeWidth, opacity),
                MarkType.AREA.key to listOf(interpolate, opacity),
                MarkType.BAR.key to listOf(opacity),
                MarkType.POINT.key to listOf(
                    SettingNode.Field(field(ChartKeys.SIZE, s.tool("field_size"), FieldType.NUMERIC, s.tool("schema_size"), mapOf("min" to 4, "max" to 900, "decimals" to 0))),
                    SettingNode.Field(field(ChartKeys.FILLED, s.tool("field_filled"), FieldType.BOOLEAN, s.tool("schema_filled"))),
                    shape, opacity),
                MarkType.TEXT.key to listOf(opacity),
                MarkType.ARC.key to listOf(opacity),
                MarkType.TICK.key to listOf(strokeWidth, opacity),
                MarkType.RECT.key to listOf(opacity)
            )
        )
    }

    /** One channel, removable whole, with what its kind takes. */
    private fun channel(channel: Channel, context: Context): SettingNode.Group {
        val s = s(context)
        val key = channel.key
        val fieldNode = SettingNode.Field(field(ChartKeys.FIELD, s.tool("field_channel_field"), FieldType.TEXT, s.tool("schema_channel_field"), short()), rowField = true)
        val type = SettingNode.Field(field(ChartKeys.TYPE, s.tool("field_measure"), FieldType.CHOICE, s.tool("schema_measure"),
            choice(Measure.entries.map { it.key }) { s.tool("measure_$it") }))
        val legend = SettingNode.Field(field(ChartKeys.LEGEND, s.tool("field_legend"), FieldType.BOOLEAN, s.tool("schema_legend")))
        fun condition(value: SettingNode.Field) = SettingNode.Group(ChartKeys.CONDITION, s.tool("field_condition"), listOf(
            SettingNode.Condition(ChartKeys.TEST, s.tool("field_test"), reference = "", emptyPeriod = null, required = true, onRow = true),
            value.copy(required = true)
        ))
        val stack = SettingNode.Field(field(ChartKeys.STACK, s.tool("field_stack"), FieldType.CHOICE, s.tool("schema_stack"),
            choice(Stack.entries.map { it.key }) { s.tool("stack_$it") }))
        val numberValue = SettingNode.Field(field(ChartKeys.VALUE, s.tool("field_value"), FieldType.NUMERIC, s.tool("schema_value_$key"), mapOf("decimals" to 2)))
        val nodes: List<SettingNode> = when (channel) {
            Channel.X, Channel.Y -> listOfNotNull(
                fieldNode, type,
                SettingNode.Group(ChartKeys.SCALE, s.tool("field_scale"), listOf(
                    SettingNode.ListOf(ChartKeys.DOMAIN, s.tool("field_domain"), SettingNode.Item.Value(field(ChartKeys.DOMAIN, s.tool("field_bound"), FieldType.NUMERIC, s.tool("schema_domain"), mapOf("decimals" to 2)))),
                    SettingNode.Field(field(ChartKeys.ZERO, s.tool("field_zero"), FieldType.BOOLEAN, s.tool("schema_zero"))),
                    SettingNode.Field(field(ChartKeys.NICE, s.tool("field_nice"), FieldType.BOOLEAN, s.tool("schema_nice"))),
                    SettingNode.Field(field(ChartKeys.REVERSE, s.tool("field_reverse"), FieldType.BOOLEAN, s.tool("schema_reverse")))
                )),
                SettingNode.Group(ChartKeys.AXIS, s.tool("field_axis"), listOfNotNull(
                    SettingNode.Field(field(ChartKeys.GRID_LINES, s.tool("field_grid"), FieldType.BOOLEAN, s.tool("schema_grid"))),
                    SettingNode.Field(field(ChartKeys.ORIENT, s.tool("field_orient"), FieldType.CHOICE, s.tool("schema_orient"),
                        choice(Orient.entries.map { it.key }) { s.tool("orient_$it") })).takeIf { channel == Channel.Y }
                )),
                stack
            )
            Channel.COLOR -> {
                val colorValue = SettingNode.Field(colorChoice(ChartKeys.VALUE, s.tool("field_color"), s.tool("schema_value_color"), context))
                listOf(
                    fieldNode, type,
                    SettingNode.Group(ChartKeys.SCALE, s.tool("field_scale"), listOf(
                        SettingNode.ListOf(ChartKeys.DOMAIN, s.tool("field_categories"), SettingNode.Item.Value(field(ChartKeys.DOMAIN, s.tool("field_category"), FieldType.TEXT, s.tool("schema_color_domain"), short()))),
                        SettingNode.ListOf(ChartKeys.RANGE, s.tool("field_range"), SettingNode.Item.Value(colorChoice(ChartKeys.RANGE, s.tool("field_color"), s.tool("schema_range"), context)))
                    )),
                    legend, condition(colorValue), colorValue
                )
            }
            Channel.SIZE, Channel.OPACITY -> listOf(fieldNode, type, condition(numberValue), numberValue)
            Channel.SHAPE -> {
                val shapeValue = SettingNode.Field(field(ChartKeys.VALUE, s.tool("field_shape"), FieldType.CHOICE, s.tool("schema_value_shape"),
                    choice(Shape.entries.map { it.key }) { s.tool("shape_$it") }))
                listOf(fieldNode, legend, condition(shapeValue), shapeValue)
            }
            Channel.STROKE_DASH -> listOf(fieldNode, legend)
            Channel.THETA -> listOf(fieldNode, type)
            Channel.X2, Channel.Y2, Channel.DETAIL, Channel.ORDER, Channel.TEXT, Channel.RADIUS -> listOf(fieldNode)
        }
        return SettingNode.Group(key, s.tool("channel_${key.lowercase()}"), nodes)
    }

    // -----------------------------------------------------------------------------------------
    // Reading and checking
    // -----------------------------------------------------------------------------------------

    override fun getRowFields(): RowFields = ChartRowFields

    override suspend fun refuseConfig(config: JSONObject, context: Context): String? = ChartCheck(context).refuse(config)

    override fun getService(context: Context): ExecutableService? = null

    override fun getDao(context: Context): Any? = null

    override fun getDatabaseEntities(): List<Class<*>> = emptyList()

    @Composable
    override fun getUsageScreen(toolInstanceId: String, configJson: String, zoneName: String, onNavigateBack: () -> Unit, onLongClick: () -> Unit, openEntryId: String?) {
        ChartScreen(toolInstanceId = toolInstanceId, onNavigateBack = onNavigateBack, onConfigureClick = onLongClick)
    }
}
