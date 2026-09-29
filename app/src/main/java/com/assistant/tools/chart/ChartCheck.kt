package com.assistant.tools.chart

import android.content.Context
import com.assistant.core.conditions.Condition
import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.ChoiceShape
import com.assistant.core.fields.EntryFilters
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.services.VariableService
import com.assistant.core.strings.Strings
import org.json.JSONObject

/**
 * What a chart's config is checked on beyond its schema, which only reading the app tells: that
 * every column a channel, a transform or a condition names is one its layer gives, that marks get
 * the channels they need, that bars and areas colored by category say how they stack, and that
 * the layers of a view put the same sort of values on a shared axis. Each refusal names the
 * layer, what is wrong, and when it can, what would do.
 */
class ChartCheck(private val context: Context) {

    private val s = Strings.`for`(tool = "chart", context = context)

    /** Why [config] cannot be stored, or null when it can. */
    suspend fun refuse(config: JSONObject): String? {
        val spec = try { ChartSpec.of(config, { s.shared(it) }, { s.tool(it) }) } catch (e: IllegalArgumentException) { return e.message }
        if (spec.layers.any { it.source is Source.Grid } && spec.period.start == null) return s.tool("error_grid_start")

        val sources = ChartSources(context)
        val repeat = spec.composition is Composition.Repeat
        val finals = spec.layers.mapIndexed { i, layer ->
            val name = s.tool("layer_name").format(i + 1)
            (layer.source as? Source.Grid)?.let { grid -> gridColumns(grid)?.let { return "$name : $it" } }
            layer.transforms.forEachIndexed { k, transform ->
                val before = try { sources.columns(layer, k) } catch (e: IllegalStateException) { return "$name : ${e.message}" }
                transform(transform, before)?.let { return "$name : $it" }
            }
            val columns = try { sources.columns(layer) } catch (e: IllegalStateException) { return "$name : ${e.message}" }
            channels(layer, columns, repeat)?.let { return "$name : $it" }
            columns
        }

        when (val composition = spec.composition) {
            is Composition.Facet -> if (finals.none { composition.field in it }) return s.tool("error_unknown_column")
                .format(ChartKeys.FACET, composition.field, finals.flatMap { it.keys }.distinct().joinToString(", "))
            is Composition.Repeat -> composition.fields.firstOrNull { f -> finals.none { f in it } }?.let { f ->
                return s.tool("error_unknown_column").format(ChartKeys.REPEAT, f, finals.flatMap { it.keys }.distinct().joinToString(", "))
            }
            else -> Unit
        }

        // The layers of a view share its axes
        var index = 0
        spec.composition.views.forEach { view ->
            val layers = view.layers.map { layer -> layer to finals[index++] }
            axes(layers)?.let { return it }
        }
        return null
    }

    /** A grid's columns: named once, with a name a formula could write, never one a row already has. */
    private fun gridColumns(grid: Source.Grid): String? {
        val names = grid.columns.map { it.name }
        names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull()?.let { return s.tool("error_column_twice").format(it) }
        names.firstOrNull { !VariableService.NAME.matches(it) }?.let { return s.tool("error_column_name").format(it) }
        names.firstOrNull { it in ChartSources.RESERVED }?.let { return s.tool("error_column_reserved").format(it, ChartSources.RESERVED.joinToString(", ")) }
        return null
    }

    private fun unknown(what: String, field: String, columns: Map<String, FieldDefinition>) =
        s.tool("error_unknown_column").format(what, field, columns.keys.joinToString(", "))

    /** A fold's columns are of one sort, a flatten's are multiple choices. */
    private fun transform(transform: Transform, columns: Map<String, FieldDefinition>): String? = when (transform) {
        is Transform.Fold -> {
            transform.fields.firstOrNull { it !in columns }?.let { unknown(ChartKeys.FOLD, it, columns) }
                ?: transform.fields.map { columns.getValue(it) }.let { fields ->
                    fields.firstOrNull { signature(it) != signature(fields.first()) }?.let { other ->
                        s.tool("error_fold_mixed").format(fields.first().displayName, other.displayName)
                    }
                }
        }
        is Transform.Flatten -> transform.fields.firstOrNull { it !in columns }?.let { unknown(ChartKeys.FLATTEN, it, columns) }
            ?: transform.fields.firstOrNull { f ->
                val field = columns.getValue(f)
                field.type != FieldType.CHOICE || !ChoiceSettings.fromConfig(field.config).shape.isList
            }?.let { s.tool("error_flatten_choice").format(it) }
    }

    /** Every channel names a column there is, and the mark gets what it needs. */
    private fun channels(layer: Layer, columns: Map<String, FieldDefinition>, repeat: Boolean): String? {
        layer.encoding.forEach { (channel, def) ->
            def.field?.let { f -> if (f !in columns && !(repeat && f == ChartKeys.REPEAT)) return unknown(channel.key, f, columns) }
            if (def.field == null && def.value == null && def.condition == null) return s.tool("error_channel_empty").format(channel.key)
            if (channel == Channel.X || channel == Channel.Y) {
                def.scale.domain?.let { domain -> if (domain.size != 2 || domain.any { it !is Number }) return s.tool("error_domain").format(channel.key) }
            }
            def.condition?.let { condition -> condition(channel, condition.test, columns)?.let { return it } }
        }
        val type = layer.mark.type
        val has = { c: Channel -> layer.channel(c) != null }
        when {
            type == MarkType.ARC && !has(Channel.THETA) -> return s.tool("error_mark_needs").format(type.key, Channel.THETA.key)
            type == MarkType.TEXT && !has(Channel.TEXT) -> return s.tool("error_mark_needs").format(type.key, Channel.TEXT.key)
            (type == MarkType.LINE || type == MarkType.AREA) && !(has(Channel.X) && has(Channel.Y)) -> return s.tool("error_mark_needs").format(type.key, "x, y")
            type != MarkType.ARC && type != MarkType.TEXT && (has(Channel.THETA) || has(Channel.RADIUS)) -> return s.tool("error_channel_mark").format(Channel.THETA.key, type.key)
            type != MarkType.ARC && !has(Channel.X) && !has(Channel.Y) && !has(Channel.THETA) -> return s.tool("error_mark_needs").format(type.key, "x, y")
            has(Channel.X2) && !has(Channel.X) -> return s.tool("error_mark_needs").format(Channel.X2.key, Channel.X.key)
            has(Channel.Y2) && !has(Channel.Y) -> return s.tool("error_mark_needs").format(Channel.Y2.key, Channel.Y.key)
        }
        // Vega-Lite stacks bars and areas as soon as a category splits them: here it is said
        val stacked = listOf(Channel.X, Channel.Y).mapNotNull { layer.channel(it)?.stack }
        if (stacked.isNotEmpty() && type != MarkType.BAR && type != MarkType.AREA) return s.tool("error_stack_mark").format(type.key)
        if ((type == MarkType.BAR || type == MarkType.AREA) && splits(layer, columns) && stacked.isEmpty()) {
            return s.tool("error_stack_required").format(type.key, Stack.entries.joinToString(", ") { it.key })
        }
        return null
    }

    /** Whether a category splits the layer's marks into series. */
    private fun splits(layer: Layer, columns: Map<String, FieldDefinition>): Boolean = listOf(Channel.COLOR, Channel.DETAIL).any { c ->
        val def = layer.channel(c) ?: return@any false
        val field = def.field?.let { columns[it] } ?: return@any false
        val measure = def.type ?: ChartValues.measureOf(field)
        measure == Measure.NOMINAL || measure == Measure.ORDINAL || c == Channel.DETAIL
    }

    /** A channel's condition: the Condition brick on the row's columns, sides of one type. */
    private fun condition(channel: Channel, test: JSONObject, columns: Map<String, FieldDefinition>): String? {
        val read = try { Condition.fromJson(test, channel.key) { s.shared(it) } } catch (e: IllegalArgumentException) { return e.message }
        val left = read.left as? Condition.Side.Field ?: return s.tool("error_condition_left").format(channel.key)
        val field = columns[left.path] ?: return unknown("${channel.key}.${ChartKeys.CONDITION}", left.path, columns)
        if (read.op !in EntryFilters.operatorsFor(field.type)) return s.tool("error_condition_op").format(channel.key, read.op.key, field.displayName)
        read.right.forEach { side ->
            when (side) {
                is Condition.Side.Field -> {
                    val other = columns[side.path] ?: return unknown("${channel.key}.${ChartKeys.CONDITION}", side.path, columns)
                    if (other.type != field.type) return s.tool("error_condition_types").format(channel.key, field.displayName, other.displayName)
                }
                is Condition.Side.Of -> if (side.term !is com.assistant.core.terms.Term.Constant) return s.tool("error_condition_written").format(channel.key)
            }
        }
        return null
    }

    /**
     * The layers of one view share their horizontal axis, and each side its vertical one: the
     * values put there are of one sort (instants, quantities, categories), and on a vertical axis
     * of one unit — kcal with kcal. Another unit goes to the other side.
     */
    private fun axes(layers: List<Pair<Layer, Map<String, FieldDefinition>>>): String? {
        val drawn = layers.filter { (layer, _) -> layer.mark.type != MarkType.ARC && layer.channel(Channel.THETA) == null }
        fun kind(def: ChannelDef?, columns: Map<String, FieldDefinition>): String? {
            val field = def?.field?.let { columns[it] } ?: return null
            return when (def.type ?: ChartValues.measureOf(field)) {
                Measure.TEMPORAL -> "time"
                Measure.QUANTITATIVE -> "quantity"
                else -> "category"
            }
        }
        val xs = drawn.mapNotNull { (layer, columns) -> kind(layer.channel(Channel.X), columns)?.let { it to layer.channel(Channel.X)!!.field!! } }
        xs.firstOrNull { it.first != xs.first().first }?.let { other -> return s.tool("error_axis_x").format(xs.first().second, other.second) }
        Orient.entries.forEach { side ->
            val ys = drawn.filter { (layer, _) -> (layer.channel(Channel.Y)?.axis?.orient ?: Orient.LEFT) == side }
                .mapNotNull { (layer, columns) -> layer.channel(Channel.Y)?.let { def -> def.field?.let { columns[it] }?.let { field ->
                    // A stack in percent is its own unit
                    val unit = if (def.stack == Stack.NORMALIZE) "%" else signature(field)
                    Triple(kind(def, columns), unit, field.displayName)
                } } }
            ys.firstOrNull { it.first != ys.first().first || (it.first == "quantity" && it.second != ys.first().second) }?.let { other ->
                val elsewhere = if (side == Orient.LEFT) Orient.RIGHT else Orient.LEFT
                return s.tool("error_axis_y").format(ys.first().third, other.third, elsewhere.key)
            }
        }
        return null
    }

    /** What makes values comparable on one axis: their type, and their unit when they have one. */
    private fun signature(field: FieldDefinition): String = when (field.type) {
        FieldType.DURATION, FieldType.TIME, FieldType.DATETIME, FieldType.DATE -> field.type.name
        FieldType.NUMERIC, FieldType.RANGE, FieldType.SCALE -> "number:${(field.config?.get("unit") as? String)?.trim().orEmpty()}"
        else -> field.type.name
    }
}
