package com.assistant.tools.chart

import com.assistant.core.selection.EntryPeriod
import com.assistant.core.selection.EntrySelection
import com.assistant.core.terms.Term
import com.assistant.core.themes.TagColor
import com.assistant.core.ui.components.PeriodType
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a chart draws, read from its config (docs/design/missing-tools.md, « Graphique »): a
 * subset of Vega-Lite, which the AI already knows, with two departures — the data come from a
 * source of the app, never written in the config, and a color is a name of the palette.
 *
 * A chart is one drawing, which may compose: layers over one another, views one above or beside
 * the other, a small view per category (facet) or per column (repeat). Every layer reads one
 * source into a table, and draws only its columns: nothing is computed in the drawing.
 *
 * @property period The displayed period, relative to the moment the chart is shown
 */
data class ChartSpec(val period: EntryPeriod, val composition: Composition) {

    /** Every layer of the chart, whatever composes them. */
    val layers: List<Layer> get() = composition.views.flatMap { it.layers }

    companion object {
        /**
         * The chart [config] describes.
         *
         * @param shared The shared strings, for what the bricks it holds say does not read
         * @param own The chart tool's strings, for what it says itself
         * @throws IllegalArgumentException naming what does not read
         */
        fun of(config: JSONObject, shared: (String) -> String, own: (String) -> String): ChartSpec = ChartReader(shared, own).chart(config)
    }
}

/** How the views of a chart are laid out. */
sealed interface Composition {
    val views: List<View>

    /** One view, its layers over one another. */
    data class Single(val view: View) : Composition {
        override val views: List<View> get() = listOf(view)
    }

    /** Several views: one above the other, side by side, or wrapped every [columns]. */
    data class Concat(val direction: ConcatDirection, val children: List<View>, val columns: Int?) : Composition {
        override val views: List<View> get() = children
    }

    /** One small view per value of the field [field] of the rows, wrapped every [columns]. */
    data class Facet(val field: String, val view: View, val columns: Int?) : Composition {
        override val views: List<View> get() = listOf(view)
    }

    /**
     * One small view per field of [fields], the channels whose field is [ChartKeys.REPEAT] taking it
     * in turn, wrapped every [columns].
     */
    data class Repeat(val fields: List<String>, val view: View, val columns: Int?) : Composition {
        override val views: List<View> get() = listOf(view)
    }
}

enum class ConcatDirection(val key: String) { VERTICAL("vconcat"), HORIZONTAL("hconcat"), WRAP("concat") }

/** Layers drawn over one another, sharing the horizontal axis; [title] over it among others. */
data class View(val layers: List<Layer>, val title: String? = null)

/** One source read into a table, transformed, drawn with one mark. */
data class Layer(val source: Source, val transforms: List<Transform>, val mark: Mark, val encoding: Map<Channel, ChannelDef>) {
    fun channel(channel: Channel): ChannelDef? = encoding[channel]
}

/** Where a layer's rows come from. */
sealed interface Source {
    /** A row per entry of one tool: its instant, its name and its fields. */
    data class Entries(val selection: EntrySelection) : Source

    /**
     * A row per [step] of the displayed period, read at the end of the step: its instant, and a
     * column per term of [columns], a reading or a variable.
     */
    data class Grid(val step: PeriodType, val columns: List<GridColumn>) : Source
}

/** A column of a grid: its name, which the drawing names it by, and the term read at each step. */
data class GridColumn(val name: String, val term: Term)

/** What turns a layer's table into another without computing anything. */
sealed interface Transform {
    /** The [fields] turned into rows: a row per row and field, its name under [key], its value under [value]. */
    data class Fold(val fields: List<String>, val key: String, val value: String) : Transform

    /** A multiple choice of [fields] spread into a row per option. */
    data class Flatten(val fields: List<String>) : Transform
}

enum class MarkType(val key: String) {
    LINE("line"), POINT("point"), BAR("bar"), AREA("area"), TEXT("text"), ARC("arc"), TICK("tick"), RECT("rect");

    companion object {
        fun of(key: String): MarkType? = entries.firstOrNull { it.key == key }
    }
}

enum class Interpolate(val key: String) { LINEAR("linear"), STEP_AFTER("step-after"), MONOTONE("monotone") }

/** A point's shape, by Vega-Lite's name, drawn as the symbol of the same shape. */
enum class Shape(val key: String, val symbol: com.assistant.core.drawing.SymbolShape) {
    CIRCLE("circle", com.assistant.core.drawing.SymbolShape.CIRCLE), SQUARE("square", com.assistant.core.drawing.SymbolShape.SQUARE),
    TRIANGLE("triangle", com.assistant.core.drawing.SymbolShape.TRIANGLE), DIAMOND("diamond", com.assistant.core.drawing.SymbolShape.DIAMOND),
    CROSS("cross", com.assistant.core.drawing.SymbolShape.CROSS)
}

/** How a mark is drawn, whatever its data: each style only where its mark type takes it. */
data class Mark(
    val type: MarkType,
    val interpolate: Interpolate = Interpolate.LINEAR,
    val point: Boolean = false,
    val strokeDash: List<Float>? = null,
    val strokeWidth: Float? = null,
    val opacity: Float? = null,
    val size: Float? = null,
    val filled: Boolean? = null,
    val shape: Shape? = null
)

/** A channel of a mark: what of a row it shows, and how. */
enum class Channel(val key: String) {
    X("x"), Y("y"), X2("x2"), Y2("y2"), COLOR("color"), SIZE("size"), SHAPE("shape"), OPACITY("opacity"),
    STROKE_DASH("strokeDash"), DETAIL("detail"), ORDER("order"), TEXT("text"), THETA("theta"), RADIUS("radius")
}

/** The kind of values a channel shows, Vega-Lite's measurement types. */
enum class Measure(val key: String) { QUANTITATIVE("quantitative"), TEMPORAL("temporal"), ORDINAL("ordinal"), NOMINAL("nominal") }

enum class Stack(val key: String) { NONE("none"), ZERO("zero"), NORMALIZE("normalize") }

enum class Orient(val key: String) { LEFT("left"), RIGHT("right") }

/**
 * One channel: a field of the row ([field], with its [type] when the field's own is not the one
 * meant), or a [value] written for every row; [condition] gives another value to the rows it holds on.
 *
 * @property legend Whether the channel's legend is shown, at the bottom
 */
data class ChannelDef(
    val field: String? = null,
    val type: Measure? = null,
    val scale: ScaleDef = ScaleDef(),
    val axis: AxisDef = AxisDef(),
    val legend: Boolean = true,
    val stack: Stack? = null,
    val condition: ChannelCondition? = null,
    val value: Any? = null
)

/**
 * A channel's scale. [domain] fixes its bounds (two numbers) or, for categories, their order;
 * [range] the palette names the categories take in that order.
 */
data class ScaleDef(
    val domain: List<Any>? = null,
    val zero: Boolean? = null,
    val reverse: Boolean = false,
    val nice: Boolean = true,
    val range: List<TagColor>? = null
)

/** A positional channel's axis: its grid lines, and for the vertical one its side. */
data class AxisDef(val grid: Boolean? = null, val orient: Orient = Orient.LEFT)

/** [value] on the rows [test] holds on, a condition of the Condition brick on the row's fields. */
data class ChannelCondition(val test: JSONObject, val value: Any)

/** The keys of a chart's config: Vega-Lite's where it has one, the app's for its sources. */
object ChartKeys {
    const val PERIOD = "period"
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val COMPOSITION = "composition"
    const val LAYER = "layer"
    const val FACET = "facet"
    const val REPEAT = "repeat"
    const val COLUMNS = "columns"
    const val SOURCE = "source"
    const val ENTRIES = "entries"
    const val GRID = "grid"
    const val SELECTION = "selection"
    const val STEP = "step"
    const val NAME = "name"
    const val TERM = "term"
    const val TRANSFORM = "transform"
    const val FOLD = "fold"
    const val AS = "as"
    const val FLATTEN = "flatten"
    const val MARK = "mark"
    const val TYPE = "type"
    const val INTERPOLATE = "interpolate"
    const val POINT = "point"
    const val STROKE_DASH = "strokeDash"
    const val STROKE_WIDTH = "strokeWidth"
    const val OPACITY = "opacity"
    const val SIZE = "size"
    const val FILLED = "filled"
    const val SHAPE = "shape"
    const val ENCODING = "encoding"
    const val FIELD = "field"
    const val SCALE = "scale"
    const val DOMAIN = "domain"
    const val ZERO = "zero"
    const val REVERSE = "reverse"
    const val NICE = "nice"
    const val RANGE = "range"
    const val AXIS = "axis"
    const val GRID_LINES = "grid"
    const val ORIENT = "orient"
    const val LEGEND = "legend"
    const val STACK = "stack"
    const val CONDITION = "condition"
    const val TEST = "test"
    const val VALUE = "value"

    /** The compositions, the options of [COMPOSITION]. */
    const val SINGLE = "layer"
    val COMPOSITIONS = listOf(SINGLE) + ConcatDirection.entries.map { it.key } + listOf(FACET, REPEAT)

    /** The names Vega-Lite gives a fold's two columns when none are given. */
    const val FOLD_KEY = "key"
    const val FOLD_VALUE = "value"

    /** The steps a grid takes, the options of [STEP]. */
    val STEPS = mapOf("hour" to PeriodType.HOUR, "day" to PeriodType.DAY, "week" to PeriodType.WEEK, "month" to PeriodType.MONTH, "year" to PeriodType.YEAR)
}

/** Reads a chart's config into its model, naming what does not read. */
private class ChartReader(private val text: (String) -> String, private val own: (String) -> String) {

    private fun refuse(key: String, vararg args: Any): Nothing = throw IllegalArgumentException(own(key).format(*args))

    fun chart(config: JSONObject): ChartSpec {
        val period = config.optJSONObject(ChartKeys.PERIOD)?.let { EntryPeriod.fromJson(it, text) }
            ?: refuse("error_missing", ChartKeys.PERIOD)
        val composition = when (val kind = config.optString(ChartKeys.COMPOSITION).ifEmpty { ChartKeys.SINGLE }) {
            ChartKeys.SINGLE -> Composition.Single(view(config))
            ChartKeys.FACET -> Composition.Facet(
                field = config.optJSONObject(ChartKeys.FACET)?.optString(ChartKeys.FIELD)?.takeIf { it.isNotEmpty() } ?: refuse("error_missing", "${ChartKeys.FACET}.${ChartKeys.FIELD}"),
                view = view(config),
                columns = columns(config)
            )
            ChartKeys.REPEAT -> Composition.Repeat(
                fields = strings(config.optJSONArray(ChartKeys.REPEAT)).takeIf { it.isNotEmpty() } ?: refuse("error_missing", ChartKeys.REPEAT),
                view = view(config),
                columns = columns(config)
            )
            else -> {
                val direction = ConcatDirection.entries.firstOrNull { it.key == kind }
                    ?: refuse("error_option", ChartKeys.COMPOSITION, kind, ChartKeys.COMPOSITIONS.joinToString(", "))
                val children = config.optJSONArray(direction.key)?.let { array -> (0 until array.length()).map { view(array.getJSONObject(it)) } }
                    ?.takeIf { it.isNotEmpty() } ?: refuse("error_missing", direction.key)
                Composition.Concat(direction, children, columns(config))
            }
        }
        return ChartSpec(period, composition)
    }

    private fun columns(config: JSONObject): Int? = (config.opt(ChartKeys.COLUMNS) as? Number)?.toInt()?.takeIf { it > 0 }

    private fun view(json: JSONObject): View {
        val layers = json.optJSONArray(ChartKeys.LAYER)?.let { array -> (0 until array.length()).map { layer(array.getJSONObject(it), it) } }
        return View(layers?.takeIf { it.isNotEmpty() } ?: refuse("error_missing", ChartKeys.LAYER), json.optString(ChartKeys.TITLE).takeIf { it.isNotBlank() })
    }

    private fun layer(json: JSONObject, index: Int): Layer {
        val source = when (val kind = json.optString(ChartKeys.SOURCE)) {
            ChartKeys.ENTRIES -> Source.Entries(EntrySelection.fromJson(
                json.optJSONObject(ChartKeys.SELECTION)?.takeIf { it.has("target") } ?: refuse("error_missing", ChartKeys.SELECTION), text))
            ChartKeys.GRID -> Source.Grid(
                step = ChartKeys.STEPS[json.optString(ChartKeys.STEP)]
                    ?: refuse("error_option", ChartKeys.STEP, json.optString(ChartKeys.STEP), ChartKeys.STEPS.keys.joinToString(", ")),
                columns = json.optJSONArray(ChartKeys.COLUMNS)?.let { array -> (0 until array.length()).map { i ->
                    val column = array.getJSONObject(i)
                    val name = column.optString(ChartKeys.NAME).takeIf { it.isNotEmpty() } ?: refuse("error_missing", "${ChartKeys.COLUMNS}.${ChartKeys.NAME}")
                    GridColumn(name, Term.fromJson(column.optJSONObject(ChartKeys.TERM) ?: refuse("error_missing", "$name.${ChartKeys.TERM}"), name, text))
                } }?.takeIf { it.isNotEmpty() } ?: refuse("error_missing", ChartKeys.COLUMNS)
            )
            else -> refuse("error_option", ChartKeys.SOURCE, kind, "${ChartKeys.ENTRIES}, ${ChartKeys.GRID}")
        }
        val transforms = json.optJSONArray(ChartKeys.TRANSFORM)?.let { array -> (0 until array.length()).map { transform(array.getJSONObject(it)) } } ?: emptyList()
        val markJson = json.optJSONObject(ChartKeys.MARK) ?: refuse("error_missing", "${ChartKeys.LAYER}[$index].${ChartKeys.MARK}")
        val encoding = json.optJSONObject(ChartKeys.ENCODING) ?: JSONObject()
        return Layer(source, transforms, mark(markJson), Channel.entries.mapNotNull { channel ->
            encoding.optJSONObject(channel.key)?.let { channel to channelDef(channel, it) }
        }.toMap())
    }

    private fun transform(json: JSONObject): Transform {
        val fold = strings(json.optJSONArray(ChartKeys.FOLD))
        val flatten = strings(json.optJSONArray(ChartKeys.FLATTEN))
        return when {
            fold.isNotEmpty() && flatten.isEmpty() -> {
                val names = strings(json.optJSONArray(ChartKeys.AS))
                if (names.isNotEmpty() && names.size != 2) refuse("error_fold_as")
                Transform.Fold(fold, names.getOrElse(0) { ChartKeys.FOLD_KEY }, names.getOrElse(1) { ChartKeys.FOLD_VALUE })
            }
            flatten.isNotEmpty() && fold.isEmpty() -> Transform.Flatten(flatten)
            else -> refuse("error_transform")
        }
    }

    private fun mark(json: JSONObject): Mark {
        val type = MarkType.of(json.optString(ChartKeys.TYPE))
            ?: refuse("error_option", "${ChartKeys.MARK}.${ChartKeys.TYPE}", json.optString(ChartKeys.TYPE), MarkType.entries.joinToString(", ") { it.key })
        return Mark(
            type = type,
            interpolate = json.optString(ChartKeys.INTERPOLATE).takeIf { it.isNotEmpty() }?.let { key ->
                Interpolate.entries.firstOrNull { it.key == key } ?: refuse("error_option", ChartKeys.INTERPOLATE, key, Interpolate.entries.joinToString(", ") { it.key })
            } ?: Interpolate.LINEAR,
            point = json.optBoolean(ChartKeys.POINT, false),
            strokeDash = json.optJSONArray(ChartKeys.STROKE_DASH)?.let { array -> (0 until array.length()).map { (array.get(it) as Number).toFloat() } }?.takeIf { it.isNotEmpty() },
            strokeWidth = (json.opt(ChartKeys.STROKE_WIDTH) as? Number)?.toFloat(),
            opacity = (json.opt(ChartKeys.OPACITY) as? Number)?.toFloat(),
            size = (json.opt(ChartKeys.SIZE) as? Number)?.toFloat(),
            filled = if (json.has(ChartKeys.FILLED)) json.getBoolean(ChartKeys.FILLED) else null,
            shape = json.optString(ChartKeys.SHAPE).takeIf { it.isNotEmpty() }?.let { key ->
                Shape.entries.firstOrNull { it.key == key } ?: refuse("error_option", ChartKeys.SHAPE, key, Shape.entries.joinToString(", ") { it.key })
            }
        )
    }

    private fun channelDef(channel: Channel, json: JSONObject): ChannelDef {
        val scale = json.optJSONObject(ChartKeys.SCALE)
        val axis = json.optJSONObject(ChartKeys.AXIS)
        return ChannelDef(
            field = json.optString(ChartKeys.FIELD).takeIf { it.isNotEmpty() },
            type = json.optString(ChartKeys.TYPE).takeIf { it.isNotEmpty() }?.let { key ->
                Measure.entries.firstOrNull { it.key == key } ?: refuse("error_option", "${channel.key}.${ChartKeys.TYPE}", key, Measure.entries.joinToString(", ") { it.key })
            },
            scale = ScaleDef(
                domain = scale?.optJSONArray(ChartKeys.DOMAIN)?.let { array -> (0 until array.length()).map { array.get(it) } }?.takeIf { it.isNotEmpty() },
                zero = scale?.takeIf { it.has(ChartKeys.ZERO) }?.getBoolean(ChartKeys.ZERO),
                reverse = scale?.optBoolean(ChartKeys.REVERSE, false) ?: false,
                nice = scale?.optBoolean(ChartKeys.NICE, true) ?: true,
                range = scale?.optJSONArray(ChartKeys.RANGE)?.let { array -> (0 until array.length()).map { color(array.getString(it)) } }?.takeIf { it.isNotEmpty() }
            ),
            axis = AxisDef(
                grid = axis?.takeIf { it.has(ChartKeys.GRID_LINES) }?.getBoolean(ChartKeys.GRID_LINES),
                orient = axis?.optString(ChartKeys.ORIENT)?.takeIf { it.isNotEmpty() }?.let { key ->
                    Orient.entries.firstOrNull { it.key == key } ?: refuse("error_option", ChartKeys.ORIENT, key, Orient.entries.joinToString(", ") { it.key })
                } ?: Orient.LEFT
            ),
            legend = json.optBoolean(ChartKeys.LEGEND, true),
            stack = json.optString(ChartKeys.STACK).takeIf { it.isNotEmpty() }?.let { key ->
                Stack.entries.firstOrNull { it.key == key } ?: refuse("error_option", ChartKeys.STACK, key, Stack.entries.joinToString(", ") { it.key })
            },
            condition = json.optJSONObject(ChartKeys.CONDITION)?.let { condition ->
                ChannelCondition(
                    test = condition.optJSONObject(ChartKeys.TEST) ?: refuse("error_missing", "${channel.key}.${ChartKeys.CONDITION}.${ChartKeys.TEST}"),
                    value = value(channel, condition.opt(ChartKeys.VALUE) ?: refuse("error_missing", "${channel.key}.${ChartKeys.CONDITION}.${ChartKeys.VALUE}"))
                )
            },
            value = json.opt(ChartKeys.VALUE)?.takeIf { it != JSONObject.NULL }?.let { value(channel, it) }
        )
    }

    /** A value written for a channel: a palette name for a color, a number for the others that take one. */
    private fun value(channel: Channel, raw: Any): Any = when (channel) {
        Channel.COLOR -> color(raw.toString())
        Channel.SHAPE -> Shape.entries.firstOrNull { it.key == raw } ?: refuse("error_option", Channel.SHAPE.key, raw, Shape.entries.joinToString(", ") { it.key })
        else -> (raw as? Number)?.toFloat() ?: refuse("error_value", channel.key, raw)
    }

    private fun color(name: String): TagColor = TagColor.entries.firstOrNull { it.name == name }
        ?: refuse("error_option", ChartKeys.RANGE, name, TagColor.entries.joinToString(", ") { it.name })

    private fun strings(array: JSONArray?): List<String> = array?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: emptyList()

}
