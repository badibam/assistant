package com.assistant.core.charts

import com.assistant.core.conditions.Condition
import com.assistant.core.conditions.ConditionJudge
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.selection.TimeResolver
import com.assistant.core.terms.Term
import com.assistant.core.themes.TagColor
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How the texts of a chart read and measure: the app's formats for values, the theme's type for
 * their size. Given by whoever draws, so the layout makes room for what will be written.
 */
interface ChartText {
    /** The width in pixels [text] takes. */
    fun width(text: String): Float

    /** The height in pixels of a line of text. */
    val height: Float

    /** [value] as its field shows it: « 72,5 kg », « 1 h 30 », an option's label. */
    fun value(field: FieldDefinition, value: Any): String

    /** A graduation of a time axis at [instant], the axis stepping by [step]. */
    fun instant(instant: Long, step: ChartTicks.CalendarStep): String

    /** A number with [decimals] decimals, as the app writes numbers. */
    fun number(value: Double, decimals: Int): String

    /** The shared string of [key], for what the drawing writes of its own (a weekday, « % »). */
    fun shared(key: String): String
}

/** The sizes of a chart's drawing, in pixels. */
data class ChartMetrics(val density: Float) {
    fun dp(value: Float) = value * density
    val gap get() = dp(4f)
    val tickLength get() = dp(4f)
    val cellGap get() = dp(16f)
    val strokeWidth get() = dp(2f)
    val axisWidth get() = dp(1f)
    val swatch get() = dp(12f)
    val beyond get() = dp(8f)
    val minBar get() = dp(2f)
    val maxBar get() = dp(28f)
    val tickMark get() = dp(14f)
    val holeWidth get() = dp(10f)
    val slop get() = dp(24f)

    /** The height of a small view [width] pixels wide: wide enough to read, never taller than a phone holds. */
    fun cellHeight(width: Float) = (width * 0.62f).coerceIn(dp(160f), dp(300f))
}

/**
 * Lays out a chart (ChartSpec) over the tables its layers read, [width] pixels wide: the scene
 * its theme draws. Nothing here reads data or knows a screen: the tables come in whole, the
 * sizes of texts from [text].
 *
 * @param now The instant the chart is shown at, which a condition's relative dates resolve against
 */
class ChartSceneBuilder(
    private val metrics: ChartMetrics,
    private val text: ChartText,
    private val zone: ZoneId,
    private val weekStartDay: String,
    private val now: Long
) {
    private val shapes = mutableListOf<SceneShape>()
    private val hits = mutableListOf<Hit>()
    private val legends = LinkedHashMap<String, Legend>()
    private lateinit var colors: ColorScales

    /** A layer with its table, and its place among every layer of the chart. */
    private data class Bound(val layer: Layer, val table: ChartTable, val index: Int)

    /** What one small view draws: its layers, and its title for a facet or a repeat. */
    private data class Cell(val title: String?, val layers: List<Bound>)

    /**
     * @param spec The chart
     * @param tables The table of each layer, in the order of ChartSpec.layers
     * @param period The displayed period's instants, a side absent without limit
     */
    fun build(spec: ChartSpec, tables: List<ChartTable>, period: Pair<Long?, Long?>, width: Float): ChartScene {
        require(tables.size == spec.layers.size) { "a table per layer" }
        shapes.clear(); hits.clear(); legends.clear()
        periodStart = period.first?.toDouble()
        periodEnd = period.second?.toDouble()
        val bound = spec.layers.zip(tables).mapIndexed { i, (layer, table) -> Bound(layer, table, i) }
        colors = ColorScales(bound)

        val cells = cellsOf(spec.composition, bound)
        val columns = when (val c = spec.composition) {
            is Composition.Single -> 1
            is Composition.Concat -> when (c.direction) {
                ConcatDirection.VERTICAL -> 1
                ConcatDirection.HORIZONTAL -> cells.size
                ConcatDirection.WRAP -> c.columns ?: 1
            }
            is Composition.Facet -> c.columns ?: 1
            is Composition.Repeat -> c.columns ?: 1
        }.coerceIn(1, cells.size.coerceAtLeast(1))
        val cellWidth = (width - metrics.cellGap * (columns - 1)) / columns
        val cellHeight = metrics.cellHeight(if (columns == 1) cellWidth else width)
        val titleHeight = if (cells.any { it.title != null }) text.height + metrics.gap * 2 else 0f
        // A facet's views share their scales: its categories compare across them
        val shared = if (spec.composition is Composition.Facet) cells.flatMap { it.layers } else null

        var bottom = 0f
        cells.forEachIndexed { i, cell ->
            val left = (i % columns) * (cellWidth + metrics.cellGap)
            val top = (i / columns) * (cellHeight + titleHeight + metrics.cellGap)
            cell.title?.let {
                shapes.add(SceneShape.Label(it, ScenePoint(left + cellWidth / 2, top + metrics.gap), TextAnchor.MIDDLE, TextBaseline.TOP, null, SceneRole.CELL_TITLE))
            }
            val rect = SceneRect(left, top + titleHeight, left + cellWidth, top + titleHeight + cellHeight)
            if (cell.layers.all { it.layer.mark.type == MarkType.ARC || (it.layer.mark.type == MarkType.TEXT && it.layer.channel(Channel.THETA) != null) }) {
                drawArcs(cell, rect)
            } else {
                drawCartesian(cell, rect, shared)
            }
            bottom = max(bottom, rect.bottom)
        }
        val height = drawLegends(bottom + metrics.cellGap, width)
        return ChartScene(width, height, shapes.toList(), hits.toList())
    }

    // ---------------------------------------------------------------------------------------
    // Composition
    // ---------------------------------------------------------------------------------------

    private fun cellsOf(composition: Composition, bound: List<Bound>): List<Cell> = when (composition) {
        is Composition.Single -> listOf(Cell(null, bound))
        is Composition.Concat -> {
            var next = 0
            composition.children.map { view -> Cell(view.title, view.layers.map { bound[next++] }) }
        }
        is Composition.Facet -> {
            val field = composition.field
            val holding = bound.filter { field in it.table.columns }
            val definition = holding.firstOrNull()?.table?.columns?.get(field)
            val raw = LinkedHashMap<String, Any>()
            holding.forEach { b -> b.table.rows.forEach { row -> row.value(field)?.let { v -> ChartValues.category(v)?.let { raw.putIfAbsent(it, v) } } } }
            ChartValues.categories(raw.keys.toList(), definition, null, ordered = false).map { category ->
                Cell(
                    title = definition?.let { text.value(it, raw.getValue(category)) } ?: category,
                    layers = bound.map { b ->
                        if (field !in b.table.columns) b
                        else b.copy(table = b.table.copy(rows = b.table.rows.filter { ChartValues.category(it.value(field)) == category }))
                    }
                )
            }
        }
        is Composition.Repeat -> composition.fields.map { field ->
            Cell(
                title = bound.firstNotNullOfOrNull { it.table.columns[field] }?.let { title(it) } ?: field,
                layers = bound.map { b ->
                    b.copy(layer = b.layer.copy(encoding = b.layer.encoding.mapValues { (_, def) -> if (def.field == ChartKeys.REPEAT) def.copy(field = field) else def }))
                }
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Cartesian views
    // ---------------------------------------------------------------------------------------

    /** Where a row stands on one axis, before any scale: a number, or a category. */
    private sealed interface Pos {
        data class Num(val value: Double) : Pos
        data class Cat(val key: String) : Pos
    }

    /** How an axis is scaled. */
    private enum class AxisKind { LINEAR, TIME, BAND }

    /**
     * A row ready to draw: its places on both axes, a stacked or ranged value's two ends, the
     * failure it carries if a field it draws could not be read.
     */
    private data class Placed(
        val row: Row,
        val x: Pos?, val x2: Double?,
        val y: Pos?, val y2: Double?,
        /** The ends of the value along the value axis once stacked or ranged; null for a plain value */
        val from: Double?, val to: Double?,
        val failure: Cell.Failed?
    )

    /** A layer read against its channels: its measures, its side, its rows placed. */
    private inner class Prepared(val bound: Bound) {
        val layer get() = bound.layer
        val table get() = bound.table
        val xDef = layer.channel(Channel.X)
        val yDef = layer.channel(Channel.Y)
        val xField = xDef?.field?.let { table.columns[it] }
        val yField = yDef?.field?.let { table.columns[it] }
        val xMeasure = xDef?.let { it.type ?: xField?.let(ChartValues::measureOf) }
        val yMeasure = yDef?.let { it.type ?: yField?.let(ChartValues::measureOf) }
        val side = yDef?.axis?.orient ?: Orient.LEFT

        /** Bars and areas on a band or time axis stand up; on a band y against a quantity they lie down. */
        val horizontal = (layer.mark.type == MarkType.BAR || layer.mark.type == MarkType.AREA) &&
            isBand(yMeasure) && xMeasure == Measure.QUANTITATIVE

        /** The channel whose value a bar or an area stacks: y standing, x lying. */
        val valueDef get() = if (horizontal) xDef else yDef
        val stack: Stack = when {
            layer.mark.type != MarkType.BAR && layer.mark.type != MarkType.AREA -> Stack.NONE
            else -> valueDef?.stack ?: Stack.NONE
        }

        val rows: List<Placed> = place()

        private fun pos(def: ChannelDef?, field: FieldDefinition?, measure: Measure?, row: Row): Pos? {
            val name = def?.field ?: return null
            val value = row.value(name) ?: return null
            return if (isBand(measure)) ChartValues.category(value)?.let { Pos.Cat(it) }
            else ChartValues.number(field ?: return null, value, zone)?.let { Pos.Num(it) }
        }

        private fun place(): List<Placed> {
            val used = layer.encoding.values.mapNotNull { it.field }.toSet()
            val placed = table.rows.map { row ->
                val failure = used.firstNotNullOfOrNull { row.failure(it) }
                val x2 = layer.channel(Channel.X2)?.field?.let { f -> table.columns[f]?.let { ChartValues.number(it, row.value(f), zone) } }
                    ?: xField?.let { ChartValues.end(it, xDef?.field?.let(row::value)) }
                val y2 = layer.channel(Channel.Y2)?.field?.let { f -> table.columns[f]?.let { ChartValues.number(it, row.value(f), zone) } }
                    ?: yField?.let { ChartValues.end(it, yDef?.field?.let(row::value)) }
                Placed(row, pos(xDef, xField, xMeasure, row), x2, pos(yDef, yField, yMeasure, row), y2, null, null, failure)
            }
            return if (stack == Stack.NONE) placed.map { p ->
                // A bar or an area rises from zero; a range spans its two ends
                val value = (if (horizontal) p.x else p.y) as? Pos.Num
                val end = if (horizontal) p.x2 else p.y2
                when {
                    value == null -> p
                    end != null -> p.copy(from = value.value, to = end)
                    layer.mark.type == MarkType.BAR || layer.mark.type == MarkType.AREA -> p.copy(from = 0.0, to = value.value)
                    else -> p
                }
            } else stacked(placed)
        }

        /**
         * Values piled along the value axis at each place of the other: positive ones up from
         * zero, negative ones down, in the order of the series (the order channel, else the
         * colors' order); normalized, each place sums to 1.
         */
        private fun stacked(placed: List<Placed>): List<Placed> {
            val orderField = layer.channel(Channel.ORDER)?.field
            fun place(p: Placed): Any? = when (val at = if (horizontal) p.y else p.x) {
                is Pos.Cat -> at.key
                is Pos.Num -> p.row.span?.first ?: at.value
                null -> ""
            }
            val result = placed.toMutableList()
            placed.indices.groupBy { place(placed[it]) }.values.forEach { group ->
                val ordered = group.sortedWith(compareBy<Int> { i ->
                    orderField?.let { f -> table.columns[f]?.let { ChartValues.number(it, placed[i].row.value(f), zone) } } ?: 0.0
                }.thenBy { i -> colors.rank(layer, placed[i].row) })
                val total = ordered.sumOf { i -> abs(((if (horizontal) placed[i].x else placed[i].y) as? Pos.Num)?.value ?: 0.0) }
                var up = 0.0
                var down = 0.0
                ordered.forEach { i ->
                    val raw = ((if (horizontal) placed[i].x else placed[i].y) as? Pos.Num)?.value ?: return@forEach
                    val value = if (stack == Stack.NORMALIZE) (if (total == 0.0) 0.0 else raw / total) else raw
                    result[i] = if (value >= 0) placed[i].copy(from = up, to = up + value).also { up += value }
                    else placed[i].copy(from = down + value, to = down).also { down += value }
                }
            }
            return result
        }
    }

    private fun isBand(measure: Measure?) = measure == Measure.NOMINAL || measure == Measure.ORDINAL

    /** An axis's scale once laid out, with its graduations. */
    private inner class Axis(val kind: AxisKind, val field: FieldDefinition?, val normalized: Boolean) {
        var linear: LinearScale? = null
        var band: BandScale? = null
        var ticks: List<Tick> = emptyList()
        var fixed: Pair<Double, Double>? = null
        val rawCategories = LinkedHashMap<String, Any>()

        fun position(pos: Pos?): Float? = when (pos) {
            null -> band?.let { b -> b.center(b.categories.firstOrNull() ?: return null) }
            is Pos.Num -> linear?.position(pos.value)
            is Pos.Cat -> band?.center(pos.key)
        }

        fun number(value: Double): Float? = linear?.position(value)

        /** Whether [value] stands inside fixed bounds; always, without any. */
        fun inside(value: Double) = fixed?.let { (lo, hi) -> value >= min(lo, hi) - 1e-9 && value <= max(lo, hi) + 1e-9 } ?: true

        /**
         * For [value] past fixed bounds, the pixel of the bound it passes, and whether that bound
         * lies toward the start of the pixels (the top of a vertical axis, reversed or not).
         */
        fun edge(value: Double): Pair<Float, Boolean>? {
            val (lo, hi) = fixed ?: return null
            val bound = if (value > max(lo, hi)) max(lo, hi) else min(lo, hi)
            val other = if (bound == max(lo, hi)) min(lo, hi) else max(lo, hi)
            val at = number(bound) ?: return null
            return at to (at < (number(other) ?: at))
        }
    }

    private fun drawCartesian(cell: Cell, rect: SceneRect, shared: List<Bound>?) {
        val prepared = cell.layers.map { Prepared(it) }
        val sharedPrepared = shared?.map { Prepared(it) } ?: prepared
        val sides = listOf(Orient.LEFT, Orient.RIGHT).filter { side -> prepared.any { it.side == side && it.yDef != null } || (side == Orient.LEFT && prepared.none { it.yDef != null }) }

        // The axes' kinds and fields: the first layer that says
        val xKind = kindOf(prepared.map { it.xMeasure })
        val xField = prepared.firstNotNullOfOrNull { it.xField }
        val yKinds = sides.associateWith { side -> kindOf(prepared.filter { it.side == side }.map { it.yMeasure }) }

        // Room: titles over the plot, graduations beside and under it
        val yTitles = sides.associateWith { side -> prepared.firstOrNull { it.side == side && it.yField != null }?.let { yTitle(it) } }
        val top = rect.top + (if (yTitles.values.any { it != null }) text.height + metrics.gap * 2 else metrics.gap)
        val xTitle = xField?.takeIf { xKind != AxisKind.TIME }?.let { xTitleOf(prepared) }
        val bottom = rect.bottom - text.height - metrics.tickLength - metrics.gap - (if (xTitle != null) text.height + metrics.gap else 0f)

        val yAxes = sides.associateWith { side ->
            val layers = sharedPrepared.filter { it.side == side || (it.yDef == null && side == Orient.LEFT) }
            axis(layers, vertical = true, kind = yKinds.getValue(side), start = bottom, end = top)
        }
        val margins = sides.associateWith { side ->
            val axis = yAxes.getValue(side)
            (axis.ticks.maxOfOrNull { text.width(it.label) } ?: 0f) + metrics.tickLength + metrics.gap * 2
        }
        val left = rect.left + (margins[Orient.LEFT] ?: metrics.gap)
        val right = rect.right - (margins[Orient.RIGHT] ?: metrics.gap * 2)
        val xAxis = axis(sharedPrepared, vertical = false, kind = xKind, start = left, end = right)
        val plot = SceneRect(left, top, right, bottom)

        // Behind the marks: the grid
        yAxes.forEach { (side, axis) ->
            val grid = prepared.firstOrNull { it.side == side }?.yDef?.axis?.grid ?: (side == Orient.LEFT && axis.kind != AxisKind.BAND)
            if (grid) axis.ticks.forEach { tick -> axis.number(tick.value)?.let { y ->
                shapes.add(SceneShape.Segment(ScenePoint(plot.left, y), ScenePoint(plot.right, y), null, metrics.axisWidth, null, SceneRole.GRID))
            } }
        }
        if (prepared.firstOrNull()?.xDef?.axis?.grid == true) xAxis.ticks.forEach { tick -> xAxis.number(tick.value)?.let { x ->
            shapes.add(SceneShape.Segment(ScenePoint(x, plot.top), ScenePoint(x, plot.bottom), null, metrics.axisWidth, null, SceneRole.GRID))
        } }

        // The marks, layer over layer, then the holes over them
        prepared.forEach { p ->
            val yAxis = yAxes[p.side] ?: yAxes.values.first()
            drawLayer(p, xAxis, yAxis, plot)
        }
        prepared.forEach { p -> drawHoles(p, xAxis, plot) }

        // The axes over everything
        shapes.add(SceneShape.Segment(ScenePoint(plot.left, plot.bottom), ScenePoint(plot.right, plot.bottom), null, metrics.axisWidth, null, SceneRole.AXIS))
        xAxis.ticks.forEach { tick ->
            val x = (if (xAxis.kind == AxisKind.BAND) xAxis.band?.center(categoryOf(xAxis, tick)) else xAxis.number(tick.value)) ?: return@forEach
            shapes.add(SceneShape.Segment(ScenePoint(x, plot.bottom), ScenePoint(x, plot.bottom + metrics.tickLength), null, metrics.axisWidth, null, SceneRole.TICK))
            shapes.add(SceneShape.Label(tick.label, ScenePoint(x, plot.bottom + metrics.tickLength + metrics.gap), TextAnchor.MIDDLE, TextBaseline.TOP, null, SceneRole.AXIS_LABEL))
        }
        xTitle?.let {
            shapes.add(SceneShape.Label(it, ScenePoint((plot.left + plot.right) / 2, rect.bottom), TextAnchor.MIDDLE, TextBaseline.BOTTOM, null, SceneRole.AXIS_TITLE))
        }
        yAxes.forEach { (side, axis) ->
            val x = if (side == Orient.LEFT) plot.left else plot.right
            val outward = if (side == Orient.LEFT) -1 else 1
            shapes.add(SceneShape.Segment(ScenePoint(x, plot.top), ScenePoint(x, plot.bottom), null, metrics.axisWidth, null, SceneRole.AXIS))
            axis.ticks.forEach { tick ->
                val y = (if (axis.kind == AxisKind.BAND) axis.band?.center(categoryOf(axis, tick)) else axis.number(tick.value)) ?: return@forEach
                shapes.add(SceneShape.Segment(ScenePoint(x, y), ScenePoint(x + outward * metrics.tickLength, y), null, metrics.axisWidth, null, SceneRole.TICK))
                shapes.add(SceneShape.Label(tick.label, ScenePoint(x + outward * (metrics.tickLength + metrics.gap), y),
                    if (side == Orient.LEFT) TextAnchor.END else TextAnchor.START, TextBaseline.MIDDLE, null, SceneRole.AXIS_LABEL))
            }
            yTitles[side]?.let { title ->
                shapes.add(SceneShape.Label(title, ScenePoint(if (side == Orient.LEFT) rect.left else rect.right, rect.top + metrics.gap),
                    if (side == Orient.LEFT) TextAnchor.START else TextAnchor.END, TextBaseline.TOP, null, SceneRole.AXIS_TITLE))
            }
        }
    }

    /** A band axis's graduation carries its category as its value's index. */
    private fun categoryOf(axis: Axis, tick: Tick): String = axis.band?.categories?.getOrNull(tick.value.toInt()) ?: ""

    private fun kindOf(measures: List<Measure?>): AxisKind = when {
        measures.any { it == Measure.TEMPORAL } -> AxisKind.TIME
        measures.any { isBand(it) } -> AxisKind.BAND
        measures.any { it == Measure.QUANTITATIVE } -> AxisKind.LINEAR
        else -> AxisKind.BAND
    }

    /** An axis's title: its column's name with its unit, « Poids (kg) »; a share once normalized. */
    private fun yTitle(p: Prepared): String? {
        val field = p.yField ?: return null
        return if (p.stack == Stack.NORMALIZE && !p.horizontal) text.shared("chart_axis_share").format(field.displayName) else title(field)
    }

    private fun xTitleOf(prepared: List<Prepared>): String? {
        val p = prepared.firstOrNull { it.xField != null } ?: return null
        return if (p.stack == Stack.NORMALIZE && p.horizontal) text.shared("chart_axis_share").format(p.xField!!.displayName) else title(p.xField!!)
    }

    private fun title(field: FieldDefinition): String =
        (field.config?.get("unit") as? String)?.takeIf { it.isNotBlank() }?.let { "${field.displayName} ($it)" } ?: field.displayName

    /**
     * The scale of one axis from [start] to [end] pixels over the values [layers] put on it:
     * their categories in order, or their extent — the displayed period on a time axis, zero
     * included when the scale says so (Vega-Lite's default), fixed bounds when given, rounded to
     * graduations when nice.
     */
    private fun axis(layers: List<Prepared>, vertical: Boolean, kind: AxisKind, start: Float, end: Float): Axis {
        fun defOf(p: Prepared) = if (vertical) p.yDef else p.xDef
        fun measureOf(p: Prepared) = if (vertical) p.yMeasure else p.xMeasure
        fun fieldOf(p: Prepared) = if (vertical) p.yField else p.xField
        val onAxis = layers.filter { defOf(it) != null }
        val field = onAxis.firstNotNullOfOrNull { fieldOf(it) }
        val normalized = onAxis.any { it.stack == Stack.NORMALIZE && it.horizontal != vertical }
        val axis = Axis(kind, field, normalized)
        val count = ((abs(end - start)) / metrics.dp(if (vertical) 40f else 72f)).toInt().coerceAtLeast(2)
        val scale = onAxis.firstOrNull()?.let { defOf(it) }?.scale ?: ScaleDef()

        when (kind) {
            AxisKind.BAND -> {
                onAxis.forEach { p ->
                    val name = defOf(p)?.field ?: return@forEach
                    p.table.rows.forEach { row -> row.value(name)?.let { v -> ChartValues.category(v)?.let { axis.rawCategories.putIfAbsent(it, v) } } }
                }
                val ordered = onAxis.any { measureOf(it) == Measure.ORDINAL }
                val categories = ChartValues.categories(axis.rawCategories.keys.toList(), field, scale.domain, ordered).ifEmpty { listOf("") }
                val shown = if (scale.reverse) categories.reversed() else categories
                // Vertical bands read from the top down
                axis.band = if (vertical) BandScale(shown, end, start) else BandScale(shown, start, end)
                // A band's graduation holds its category's place among the bands
                axis.ticks = shown.mapIndexed { i, key ->
                    Tick(i.toDouble(), axis.rawCategories[key]?.let { raw -> field?.let { text.value(it, raw) } ?: key } ?: key)
                }.let { ticks -> thin(ticks, if (vertical) count * 2 else count) }
            }
            AxisKind.TIME, AxisKind.LINEAR -> {
                val values = onAxis.flatMap { p ->
                    p.rows.flatMap { placed ->
                        val stackedHere = p.horizontal != vertical
                        val ends = if (stackedHere) listOfNotNull(placed.from, placed.to) else emptyList()
                        val pos = ((if (vertical) placed.y else placed.x) as? Pos.Num)?.value
                        val second = if (vertical) placed.y2 else placed.x2
                        val span = if (!vertical) placed.row.span?.let { listOf(it.first.toDouble(), it.second.toDouble()) } ?: emptyList() else emptyList()
                        (if (ends.isNotEmpty()) ends else listOfNotNull(pos, second)) + span
                    }
                }
                val periodBounds = if (kind == AxisKind.TIME && !vertical) listOfNotNull(periodStart, periodEnd) else emptyList()
                val fixed = scale.domain?.mapNotNull { (it as? Number)?.toDouble() }?.takeIf { it.size == 2 }
                val zero = (scale.zero ?: (kind == AxisKind.LINEAR)) && kind == AxisKind.LINEAR
                var lo = (values + periodBounds + (if (zero) listOf(0.0) else emptyList())).minOrNull() ?: 0.0
                var hi = (values + periodBounds + (if (zero) listOf(0.0) else emptyList())).maxOrNull() ?: 1.0
                if (normalized) { lo = min(lo, 0.0); hi = max(hi, 1.0) }
                if (fixed != null) { lo = fixed[0]; hi = fixed[1]; axis.fixed = lo to hi }
                if (lo == hi) { lo -= if (lo == 0.0) 0.0 else abs(lo) * 0.1; hi += if (hi == 0.0) 1.0 else abs(hi) * 0.1 }
                axis.ticks = if (kind == AxisKind.TIME) timeTicks(lo, hi, count) else numberTicks(lo, hi, count, field, normalized)
                if (kind == AxisKind.LINEAR && scale.nice && fixed == null && axis.ticks.size >= 2) {
                    val step = axis.ticks[1].value - axis.ticks[0].value
                    val (nlo, nhi) = ChartTicks.nice(lo, hi, step)
                    lo = nlo; hi = nhi
                    axis.ticks = numberTicks(lo, hi, count, field, normalized)
                }
                axis.linear = LinearScale(lo, hi, start, end, scale.reverse)
            }
        }
        return axis
    }

    /** Every other graduation, and so on, until they are no more than [count]. */
    private fun thin(ticks: List<Tick>, count: Int): List<Tick> {
        if (ticks.size <= count) return ticks
        val every = (ticks.size + count - 1) / count
        return ticks.filterIndexed { i, _ -> i % every == 0 }
    }

    /** The displayed period, which a time axis spans whatever its rows. */
    private var periodStart: Double? = null
    private var periodEnd: Double? = null

    private fun timeTicks(lo: Double, hi: Double, count: Int): List<Tick> {
        val step = ChartTicks.calendarStep(lo.toLong(), hi.toLong(), count)
        return ChartTicks.calendar(lo.toLong(), hi.toLong(), step, zone, weekStartDay).map { Tick(it.toDouble(), text.instant(it, step)) }
    }

    /** Graduations of a quantity, written as its field writes it: durations as durations, hours as hours, shares as percents. */
    private fun numberTicks(lo: Double, hi: Double, count: Int, field: FieldDefinition?, normalized: Boolean): List<Tick> {
        if (normalized) return ChartTicks.multiples(lo, hi, ChartTicks.numberStep(lo, hi, count.coerceAtMost(5)))
            .map { Tick(it, text.shared("chart_percent").format(text.number(it * 100, 0))) }
        return when (field?.type) {
            FieldType.DURATION -> {
                val step = ChartTicks.roundStep(hi - lo, count, ChartTicks.DURATION_STEPS).toDouble()
                ChartTicks.multiples(lo, hi, step).map { Tick(it, text.value(field, it.toLong())) }
            }
            FieldType.TIME -> {
                val step = ChartTicks.roundStep(hi - lo, count, ChartTicks.CLOCK_STEPS).toDouble()
                ChartTicks.multiples(lo, hi, step).map { m ->
                    val minutes = ((m.toLong() % 1440) + 1440) % 1440
                    Tick(m, text.value(field, "%02d:%02d".format(minutes / 60, minutes % 60)))
                }
            }
            else -> {
                val step = ChartTicks.numberStep(lo, hi, count)
                ChartTicks.multiples(lo, hi, step).map { Tick(it, text.number(it, ChartTicks.decimals(step))) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Marks
    // ---------------------------------------------------------------------------------------

    private fun drawLayer(p: Prepared, xAxis: Axis, yAxis: Axis, plot: SceneRect) {
        when (p.layer.mark.type) {
            MarkType.LINE, MarkType.AREA -> if (p.horizontal) drawBars(p, xAxis, yAxis, plot) else drawSeries(p, xAxis, yAxis, plot)
            MarkType.BAR, MarkType.RECT -> drawBars(p, xAxis, yAxis, plot)
            MarkType.POINT, MarkType.TEXT, MarkType.TICK -> drawPoints(p, xAxis, yAxis, plot)
            MarkType.ARC -> Unit
        }
    }

    /** The width of a bar standing at an instant with no step of its own: the room between its neighbours. */
    private fun freeWidth(positions: List<Float>): Float {
        val gaps = positions.distinct().sorted().zipWithNext { a, b -> b - a }.filter { it > 0 }
        return ((gaps.minOrNull() ?: metrics.maxBar) * 0.8f).coerceIn(metrics.minBar, metrics.maxBar)
    }

    /** Where a row stands along an axis as an extent: its band, its step's span, or its point widened to [free]. */
    private fun extent(axis: Axis, pos: Pos?, second: Double?, span: Pair<Long, Long>?, free: Float): Pair<Float, Float>? = when {
        axis.kind == AxisKind.BAND -> {
            val band = axis.band ?: return null
            val key = (pos as? Pos.Cat)?.key ?: band.categories.firstOrNull() ?: return null
            band.bandStart(key)?.let { it to it + band.bandwidth }
        }
        span != null && axis.kind == AxisKind.TIME -> {
            val a = axis.number(span.first.toDouble()) ?: return null
            val b = axis.number((span.second + 1).toDouble()) ?: return null
            val inset = min(abs(b - a) * 0.1f, metrics.dp(2f))
            min(a, b) + inset to max(a, b) - inset
        }
        second != null && pos is Pos.Num -> {
            val a = axis.number(pos.value) ?: return null
            val b = axis.number(second) ?: return null
            min(a, b) to max(a, b)
        }
        pos is Pos.Num -> axis.number(pos.value)?.let { it - free / 2 to it + free / 2 }
        else -> null
    }

    private fun drawBars(p: Prepared, xAxis: Axis, yAxis: Axis, plot: SceneRect) {
        val base = if (p.horizontal) xAxis else yAxis
        val cross = if (p.horizontal) yAxis else xAxis
        val free = freeWidth(p.rows.mapNotNull { r -> ((if (p.horizontal) r.y else r.x) as? Pos.Num)?.let { cross.number(it.value) } })
        p.rows.forEach { r ->
            if (r.failure != null) return@forEach
            val across = extent(cross, if (p.horizontal) r.y else r.x, if (p.horizontal) null else r.x2.takeIf { p.layer.mark.type == MarkType.RECT }, r.row.span, free) ?: return@forEach
            val along: Pair<Float, Float> = if (r.from != null && r.to != null) {
                // The value passing fixed bounds is marked at the edge; a base below them starts at the edge
                if (!base.inside(r.to)) { beyond(p, r, base, across); return@forEach }
                val from = base.fixed?.let { (lo, hi) -> r.from.coerceIn(min(lo, hi), max(lo, hi)) } ?: r.from
                val a = base.number(from); val b = base.number(r.to)
                if (a == null || b == null) return@forEach
                min(a, b) to max(a, b)
            } else {
                // A rect on two bands, or on a band and a point
                extent(base, if (p.horizontal) r.x else r.y, if (p.horizontal) r.x2 else r.y2, null, free) ?: return@forEach
            }
            val rect = if (p.horizontal) SceneRect(along.first, across.first, along.second, across.second)
                else SceneRect(across.first, along.first, across.second, along.second)
            shapes.add(SceneShape.Box(rect, colorOf(p, r.row), opacityOf(p, r.row), SceneRole.MARK))
            hits.add(Hit(r.row, p.table.columns, area = rect))
        }
    }

    /** A value past fixed bounds: a mark at the edge it passes, never the value drawn against the edge. */
    private fun beyond(p: Prepared, r: Placed, base: Axis, across: Pair<Float, Float>) {
        val (edge, towardStart) = base.edge(r.to ?: return) ?: return
        val center = (across.first + across.second) / 2
        mark(p, r, if (p.horizontal) ScenePoint(edge, center) else ScenePoint(center, edge), towardStart)
    }

    /** The mark of a value past fixed bounds at [at], pointing [up], touched like the value. */
    private fun mark(p: Prepared, r: Placed, at: ScenePoint, up: Boolean) {
        shapes.add(SceneShape.Beyond(at, up, metrics.beyond, colorOf(p, r.row)))
        hits.add(Hit(r.row, p.table.columns, point = at))
    }

    /** Lines and areas: a series per color, detail and dash, in the order of the horizontal axis, broken where a value is missing. */
    private fun drawSeries(p: Prepared, xAxis: Axis, yAxis: Axis, plot: SceneRect) {
        val bySeries = p.rows.groupBy { colors.seriesKey(p.layer, it.row) }
        bySeries.values.sortedBy { rows -> colors.rank(p.layer, rows.first().row) }.forEach { rows ->
            val sorted = rows.sortedBy { r -> xAxis.position(r.x) ?: Float.MAX_VALUE }
            val runs = mutableListOf<MutableList<Pair<ScenePoint, Placed>>>()
            var current = mutableListOf<Pair<ScenePoint, Placed>>()
            val baselines = mutableListOf<MutableList<ScenePoint>>()
            var baseline = mutableListOf<ScenePoint>()
            fun cut() {
                if (current.isNotEmpty()) { runs.add(current); baselines.add(baseline) }
                current = mutableListOf(); baseline = mutableListOf()
            }
            sorted.forEach { r ->
                val x = xAxis.position(r.x)
                val value = r.to ?: (r.y as? Pos.Num)?.value
                if (r.failure != null || x == null || value == null) { cut(); return@forEach }
                if (!yAxis.inside(value)) {
                    cut()
                    yAxis.edge(value)?.let { (edge, up) -> mark(p, r, ScenePoint(x, edge), up) }
                    return@forEach
                }
                val y = (if (r.y is Pos.Cat) yAxis.position(r.y) else yAxis.number(value)) ?: return@forEach
                current.add(ScenePoint(x, y) to r)
                val from = r.from ?: yAxis.linear?.let { max(it.min, min(it.max, 0.0)) } ?: 0.0
                baseline.add(ScenePoint(x, yAxis.number(from) ?: plot.bottom))
            }
            cut()
            runs.forEachIndexed { i, run ->
                val first = run.first().second.row
                val points = run.map { it.first }
                val color = colorOf(p, first)
                if (p.layer.mark.type == MarkType.AREA) {
                    val steps = path(points, p.layer.mark.interpolate).toMutableList()
                    val back = baselines[i].reversed()
                    steps.addAll(path(back, p.layer.mark.interpolate).drop(1).let { rest -> listOf<PathStep>(PathStep.LineTo(back.first())) + rest })
                    steps.add(PathStep.Close)
                    shapes.add(SceneShape.Path(steps, color, opacityOf(p, first, default = 0.7f), 0f, null, filled = true, role = SceneRole.MARK))
                } else {
                    shapes.add(SceneShape.Path(path(points, p.layer.mark.interpolate), color, opacityOf(p, first),
                        p.layer.mark.strokeWidth?.let { metrics.dp(it) } ?: metrics.strokeWidth, dashOf(p, first), filled = false, role = SceneRole.MARK))
                }
                run.forEach { (point, r) ->
                    if (p.layer.mark.point) {
                        shapes.add(SceneShape.Symbol(point, metrics.dp(6f), Shape.CIRCLE, colorOf(p, r.row), opacityOf(p, r.row), filled = true, role = SceneRole.MARK))
                    }
                    hits.add(Hit(r.row, p.table.columns, point = point))
                }
            }
        }
    }

    /** The steps drawing a line through [points]: straight, in stairs, or smoothed without overshooting. */
    private fun path(points: List<ScenePoint>, interpolate: Interpolate): List<PathStep> {
        if (points.isEmpty()) return emptyList()
        val steps = mutableListOf<PathStep>(PathStep.MoveTo(points.first()))
        when (interpolate) {
            Interpolate.LINEAR -> points.drop(1).forEach { steps.add(PathStep.LineTo(it)) }
            Interpolate.STEP_AFTER -> points.zipWithNext { a, b ->
                steps.add(PathStep.LineTo(ScenePoint(b.x, a.y)))
                steps.add(PathStep.LineTo(b))
            }
            Interpolate.MONOTONE -> steps.addAll(Monotone.curve(points))
        }
        return steps
    }

    private fun drawPoints(p: Prepared, xAxis: Axis, yAxis: Axis, plot: SceneRect) {
        val sizeDef = p.layer.channel(Channel.SIZE)
        val sizeField = sizeDef?.field?.let { p.table.columns[it] }
        val sizes = sizeField?.let { f -> p.table.rows.mapNotNull { ChartValues.number(f, it.value(sizeDef.field!!), zone) } }
        p.rows.forEach { r ->
            if (r.failure != null) return@forEach
            val x = xAxis.position(r.x) ?: return@forEach
            val value = (r.y as? Pos.Num)?.value
            if (value != null && !yAxis.inside(value)) {
                yAxis.edge(value)?.let { (edge, up) -> mark(p, r, ScenePoint(x, edge), up) }
                return@forEach
            }
            val y = yAxis.position(r.y) ?: ((plot.top + plot.bottom) / 2)
            val point = ScenePoint(x, y)
            val color = colorOf(p, r.row)
            val opacity = opacityOf(p, r.row)
            when (p.layer.mark.type) {
                MarkType.POINT -> {
                    // Vega-Lite's size is an area in square pixels, 30 by default
                    val area = conditioned(p, Channel.SIZE, r.row) as? Float
                        ?: sizeField?.let { f -> ChartValues.number(f, r.row.value(sizeDef.field!!), zone)?.let { v -> scaled(v, sizes!!, 20f, 400f) } }
                        ?: (sizeDef?.value as? Float) ?: p.layer.mark.size ?: 30f
                    val shape = shapeOf(p, r.row)
                    shapes.add(SceneShape.Symbol(point, metrics.dp(sqrt(area)), shape, color, opacity, p.layer.mark.filled ?: false, SceneRole.MARK))
                }
                MarkType.TEXT -> {
                    val textDef = p.layer.channel(Channel.TEXT)
                    val raw = textDef?.field?.let { r.row.value(it) } ?: return@forEach
                    val shown = p.table.columns[textDef.field]?.let { text.value(it, raw) } ?: raw.toString()
                    shapes.add(SceneShape.Label(shown, point, TextAnchor.MIDDLE, TextBaseline.MIDDLE, color, SceneRole.MARK))
                }
                MarkType.TICK -> {
                    // Across the band it stands on, or short and level against two quantities
                    val vertical = yAxis.kind == AxisKind.BAND && xAxis.kind != AxisKind.BAND
                    val half = (if (vertical) yAxis.band?.bandwidth else xAxis.band?.bandwidth)?.let { it * 0.4f } ?: (metrics.tickMark / 2)
                    val width = p.layer.mark.strokeWidth?.let { metrics.dp(it) } ?: metrics.strokeWidth
                    shapes.add(if (vertical) SceneShape.Segment(ScenePoint(x, y - half), ScenePoint(x, y + half), color, width, null, SceneRole.MARK)
                        else SceneShape.Segment(ScenePoint(x - half, y), ScenePoint(x + half, y), color, width, null, SceneRole.MARK))
                }
                else -> Unit
            }
            hits.add(Hit(r.row, p.table.columns, point = point))
        }
    }

    /** [value] placed between [from] and [to] as it stands among [values]. */
    private fun scaled(value: Double, values: List<Double>, from: Float, to: Float): Float {
        val lo = values.minOrNull() ?: return from
        val hi = values.maxOrNull() ?: return to
        return if (hi == lo) (from + to) / 2 else (from + (to - from) * ((value - lo) / (hi - lo))).toFloat()
    }

    /** A hole per row that could not be read, across the plot at its place, touched for its cause. */
    private fun drawHoles(p: Prepared, xAxis: Axis, plot: SceneRect) {
        p.rows.filter { it.failure != null }.forEach { r ->
            val (a, b) = extent(xAxis, r.x, null, r.row.span, metrics.holeWidth) ?: return@forEach
            val rect = SceneRect(a, plot.top, b, plot.bottom)
            shapes.add(SceneShape.Box(rect, null, 1f, SceneRole.HOLE))
            hits.add(Hit(r.row, p.table.columns, area = rect))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Arcs
    // ---------------------------------------------------------------------------------------

    /** Slices of a whole: the theta of each row piled round the circle, in the colors' order. */
    private fun drawArcs(cell: Cell, rect: SceneRect) {
        val center = ScenePoint((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2)
        val outer = min(rect.right - rect.left, rect.bottom - rect.top) / 2 - metrics.gap * 2
        cell.layers.forEach { b ->
            val p = Prepared(b)
            val theta = b.layer.channel(Channel.THETA)?.field ?: return@forEach
            val thetaField = b.table.columns[theta] ?: return@forEach
            val radiusDef = b.layer.channel(Channel.RADIUS)
            val radiusField = radiusDef?.field?.let { b.table.columns[it] }
            val rows = b.table.rows.filter { it.failure(theta) == null }
                .sortedBy { colors.rank(b.layer, it) }
            val values = rows.map { ChartValues.number(thetaField, it.value(theta), zone)?.coerceAtLeast(0.0) ?: 0.0 }
            val total = values.sum().takeIf { it > 0 } ?: return@forEach
            val radii = radiusField?.let { f -> rows.map { ChartValues.number(f, it.value(radiusDef.field!!), zone) ?: 0.0 } }
            val maxRadius = radii?.maxOrNull()?.takeIf { it > 0 }
            var angle = 0f
            rows.forEachIndexed { i, row ->
                val sweep = (values[i] / total * 360).toFloat()
                val r = if (radii != null && maxRadius != null) (outer * sqrt(radii[i] / maxRadius)).toFloat() else outer
                val middle = Math.toRadians((angle + sweep / 2).toDouble())
                if (b.layer.mark.type == MarkType.ARC) {
                    shapes.add(SceneShape.Arc(center, 0f, r, angle, sweep, colorOf(p, row), opacityOf(p, row), SceneRole.MARK))
                    val at = ScenePoint(center.x + (r * 0.6f * kotlin.math.sin(middle)).toFloat(), center.y - (r * 0.6f * kotlin.math.cos(middle)).toFloat())
                    hits.add(Hit(row, b.table.columns, point = at))
                } else {
                    val raw = b.layer.channel(Channel.TEXT)?.field?.let { row.value(it) }
                    val shown = raw?.let { v -> b.table.columns[b.layer.channel(Channel.TEXT)!!.field]?.let { text.value(it, v) } ?: v.toString() }
                    if (shown != null) {
                        val at = ScenePoint(center.x + (r * 0.75f * kotlin.math.sin(middle)).toFloat(), center.y - (r * 0.75f * kotlin.math.cos(middle)).toFloat())
                        shapes.add(SceneShape.Label(shown, at, TextAnchor.MIDDLE, TextBaseline.MIDDLE, null, SceneRole.MARK))
                    }
                }
                angle += sweep
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Colors, styles, conditions
    // ---------------------------------------------------------------------------------------

    /** The colors of the chart: each field colored keeps one color per category across every layer and view. */
    private inner class ColorScales(bound: List<Bound>) {
        private val categorical = HashMap<String, Map<String, TagColor>>()
        private val orders = HashMap<String, List<String>>()
        private val raw = HashMap<String, LinkedHashMap<String, Any>>()
        private val quantities = HashMap<String, Pair<Double, Double>>()

        init {
            bound.forEach { b ->
                val def = b.layer.channel(Channel.COLOR) ?: return@forEach
                val name = def.field ?: return@forEach
                val field = b.table.columns[name] ?: return@forEach
                val measure = def.type ?: ChartValues.measureOf(field)
                if (isBand(measure)) {
                    val seen = raw.getOrPut(name) { LinkedHashMap() }
                    b.table.rows.forEach { row -> row.value(name)?.let { v -> ChartValues.category(v)?.let { seen.putIfAbsent(it, v) } } }
                    val order = ChartValues.categories(seen.keys.toList(), field, def.scale.domain, measure == Measure.ORDINAL)
                    val optionColors = com.assistant.core.fields.ChoiceSettings.fromConfig(field.config).colors.takeIf { field.type == FieldType.CHOICE } ?: emptyMap()
                    orders[name] = order
                    categorical[name] = order.mapIndexed { i, key -> key to (def.scale.range?.getOrNull(i) ?: optionColors[key] ?: PALETTE[i % PALETTE.size]) }.toMap()
                } else {
                    val values = b.table.rows.mapNotNull { ChartValues.number(field, it.value(name), zone) }
                    if (values.isNotEmpty()) {
                        val (lo, hi) = quantities[name] ?: (values.min() to values.max())
                        quantities[name] = min(lo, values.min()) to max(hi, values.max())
                    }
                }
            }
            bound.forEach { b ->
                listOf(Channel.SHAPE, Channel.STROKE_DASH).forEach { channel ->
                    val def = b.layer.channel(channel) ?: return@forEach
                    val name = def.field ?: return@forEach
                    val field = b.table.columns[name] ?: return@forEach
                    val seen = raw.getOrPut(name) { LinkedHashMap() }
                    b.table.rows.forEach { row -> row.value(name)?.let { v -> ChartValues.category(v)?.let { seen.putIfAbsent(it, v) } } }
                    orders[name] = ChartValues.categories(seen.keys.toList(), field, def.scale.domain, false)
                }
            }
            // Once every layer has given its categories, a legend per field shown
            bound.forEach { b ->
                listOf(Channel.COLOR, Channel.SHAPE, Channel.STROKE_DASH).forEach { channel ->
                    val def = b.layer.channel(channel)?.takeIf { it.legend } ?: return@forEach
                    val name = def.field ?: return@forEach
                    val field = b.table.columns[name] ?: return@forEach
                    legendOf(b, def, name, field, if (channel == Channel.COLOR) def.type ?: ChartValues.measureOf(field) else Measure.NOMINAL, channel)
                }
            }
        }

        fun categories(field: String): List<String> = orders[field] ?: emptyList()
        fun color(field: String, category: String): TagColor? = categorical[field]?.get(category)
        fun quantity(field: String): Pair<Double, Double>? = quantities[field]
        fun rawOf(field: String, category: String): Any? = raw[field]?.get(category)

        /** A row's series in [layer]: the categories of its color, detail and dash. */
        fun seriesKey(layer: Layer, row: Row): String =
            listOf(Channel.COLOR, Channel.DETAIL, Channel.STROKE_DASH).joinToString("|") { c ->
                layer.channel(c)?.field?.let { ChartValues.category(row.value(it)) } ?: ""
            }

        /** Where a row's series comes in [layer]: its color's place among the colors, the uncolored last. */
        fun rank(layer: Layer, row: Row): Int {
            val field = layer.channel(Channel.COLOR)?.field ?: return Int.MAX_VALUE
            return ChartValues.category(row.value(field))?.let { categories(field).indexOf(it) }?.takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    }

    /** A legend at the bottom: a title, and a sample and a text per category or per end of a quantity. */
    private data class Legend(val title: String, val items: List<Pair<LegendSample, String>>)

    private sealed interface LegendSample {
        data class Square(val color: SceneColor) : LegendSample
        data class Line(val color: SceneColor, val dash: List<Float>?) : LegendSample
        data class Symbol(val color: SceneColor, val shape: Shape) : LegendSample
    }

    private fun ColorScales.legendOf(b: Bound, def: ChannelDef, name: String, field: FieldDefinition, measure: Measure, channel: Channel = Channel.COLOR) {
        val key = "${channel.key}:$name"
        if (key in legends) return
        val base = SceneColor.Tag(PALETTE[b.index % PALETTE.size])
        val items = if (channel == Channel.COLOR && !isBand(measure)) {
            val (lo, hi) = quantity(name) ?: return
            val (from, to) = ramp(def)
            listOf(LegendSample.Square(SceneColor.Mix(from, to, 0f)) to text.value(field, number(field, lo)),
                LegendSample.Square(SceneColor.Mix(from, to, 1f)) to text.value(field, number(field, hi)))
        } else categories(name).mapIndexed { i, category ->
            val label = rawOf(name, category)?.let { text.value(field, it) } ?: category
            val color = if (channel == Channel.COLOR) SceneColor.Tag(color(name, category) ?: PALETTE[i % PALETTE.size]) else base
            val sample = when {
                channel == Channel.SHAPE -> LegendSample.Symbol(color, Shape.entries[i % Shape.entries.size])
                channel == Channel.STROKE_DASH -> LegendSample.Line(color, DASHES[i % DASHES.size])
                b.layer.mark.type == MarkType.LINE -> LegendSample.Line(color, null)
                b.layer.mark.type == MarkType.POINT -> LegendSample.Symbol(color, b.layer.mark.shape ?: Shape.CIRCLE)
                else -> LegendSample.Square(color)
            }
            sample to label
        }
        legends[key] = Legend(title(field), items)
    }

    /** A number back in the stored form of [field], for its format: a duration's milliseconds are whole. */
    private fun number(field: FieldDefinition, value: Double): Any = when (field.type) {
        FieldType.DURATION, FieldType.DATETIME -> value.toLong()
        else -> value
    }

    private fun ramp(def: ChannelDef): Pair<TagColor, TagColor> =
        (def.scale.range?.getOrNull(0) ?: TagColor.GREY) to (def.scale.range?.getOrNull(1) ?: TagColor.BLUE)

    /** The legends in rows at [top], wrapping at [width]; the height of the whole chart. */
    private fun drawLegends(top: Float, width: Float): Float {
        if (legends.isEmpty()) return top - metrics.cellGap
        var y = top
        val lineHeight = max(text.height, metrics.swatch) + metrics.gap
        legends.values.forEach { legend ->
            var x = 0f
            fun place(itemWidth: Float): ScenePoint {
                if (x > 0 && x + itemWidth > width) { x = 0f; y += lineHeight }
                return ScenePoint(x, y).also { x += itemWidth + metrics.gap * 3 }
            }
            val titleWidth = text.width(legend.title)
            val at = place(titleWidth)
            shapes.add(SceneShape.Label(legend.title, ScenePoint(at.x, at.y + lineHeight / 2), TextAnchor.START, TextBaseline.MIDDLE, null, SceneRole.LEGEND_TITLE))
            legend.items.forEach { (sample, label) ->
                val itemAt = place(metrics.swatch + metrics.gap + text.width(label))
                val middle = itemAt.y + lineHeight / 2
                val s = metrics.swatch
                shapes.add(when (sample) {
                    is LegendSample.Square -> SceneShape.Box(SceneRect(itemAt.x, middle - s / 2, itemAt.x + s, middle + s / 2), sample.color, 1f, SceneRole.LEGEND_SWATCH)
                    is LegendSample.Line -> SceneShape.Segment(ScenePoint(itemAt.x, middle), ScenePoint(itemAt.x + s, middle), sample.color, metrics.strokeWidth, sample.dash?.map { metrics.dp(it) }, SceneRole.LEGEND_SWATCH)
                    is LegendSample.Symbol -> SceneShape.Symbol(ScenePoint(itemAt.x + s / 2, middle), s * 0.8f, sample.shape, sample.color, 1f, true, SceneRole.LEGEND_SWATCH)
                })
                shapes.add(SceneShape.Label(label, ScenePoint(itemAt.x + s + metrics.gap, middle), TextAnchor.START, TextBaseline.MIDDLE, null, SceneRole.LEGEND_LABEL))
            }
            y += lineHeight
        }
        return y
    }

    /** A row's color: its condition's when it holds, its category's, its quantity's, the value written, or its layer's own. */
    private fun colorOf(p: Prepared, row: Row): SceneColor {
        (conditioned(p, Channel.COLOR, row) as? TagColor)?.let { return SceneColor.Tag(it) }
        val def = p.layer.channel(Channel.COLOR)
        val name = def?.field
        if (name != null) {
            val field = p.table.columns[name]
            val measure = def.type ?: field?.let(ChartValues::measureOf)
            if (isBand(measure)) {
                val category = ChartValues.category(row.value(name))
                category?.let { colors.color(name, it) }?.let { return SceneColor.Tag(it) }
            } else if (field != null) {
                val value = ChartValues.number(field, row.value(name), zone)
                val range = colors.quantity(name)
                if (value != null && range != null) {
                    val (from, to) = ramp(def)
                    val t = if (range.second == range.first) 1f else ((value - range.first) / (range.second - range.first)).toFloat()
                    return SceneColor.Mix(from, to, t)
                }
            }
        }
        (def?.value as? TagColor)?.let { return SceneColor.Tag(it) }
        return SceneColor.Tag(PALETTE[p.bound.index % PALETTE.size])
    }

    private fun opacityOf(p: Prepared, row: Row, default: Float = 1f): Float {
        (conditioned(p, Channel.OPACITY, row) as? Float)?.let { return it.coerceIn(0f, 1f) }
        val def = p.layer.channel(Channel.OPACITY)
        def?.field?.let { name ->
            val field = p.table.columns[name] ?: return@let
            val values = p.table.rows.mapNotNull { ChartValues.number(field, it.value(name), zone) }
            ChartValues.number(field, row.value(name), zone)?.let { return scaled(it, values, 0.25f, 1f) }
        }
        (def?.value as? Float)?.let { return it.coerceIn(0f, 1f) }
        return p.layer.mark.opacity?.coerceIn(0f, 1f) ?: default
    }

    private fun shapeOf(p: Prepared, row: Row): Shape {
        (conditioned(p, Channel.SHAPE, row) as? Shape)?.let { return it }
        val def = p.layer.channel(Channel.SHAPE)
        def?.field?.let { name ->
            val category = ChartValues.category(row.value(name)) ?: return@let
            val i = colors.categories(name).indexOf(category)
            if (i >= 0) return Shape.entries[i % Shape.entries.size]
        }
        return (def?.value as? Shape) ?: p.layer.mark.shape ?: Shape.CIRCLE
    }

    private fun dashOf(p: Prepared, row: Row): List<Float>? {
        val def = p.layer.channel(Channel.STROKE_DASH)
        def?.field?.let { name ->
            val category = ChartValues.category(row.value(name)) ?: return@let
            val i = colors.categories(name).indexOf(category)
            if (i >= 0) return DASHES[i % DASHES.size]?.map { metrics.dp(it) }
        }
        return p.layer.mark.strokeDash?.map { metrics.dp(it) }
    }

    /**
     * The value [channel]'s condition gives [row] when it holds on it, null otherwise: the
     * Condition brick, its fields the row's columns. A condition that cannot be judged (a value
     * missing) does not hold.
     */
    private fun conditioned(p: Prepared, channel: Channel, row: Row): Any? {
        val condition = p.layer.channel(channel)?.condition ?: return null
        val read = runCatching { Condition.fromJson(condition.test, channel.key, text::shared) }.getOrNull() ?: return null
        fun side(side: Condition.Side): Any? = when (side) {
            is Condition.Side.Field -> row.value(side.path)
            is Condition.Side.Of -> (side.term as? Term.Constant)?.value
        }
        val left = read.left as? Condition.Side.Field ?: return null
        val field = p.table.columns[left.path] ?: return null
        val holds = runCatching {
            ConditionJudge.holds(field, read.op, side(read.left), read.right.map { side(it) }, TimeResolver(now, zone, 0, weekStartDay), text::shared)
        }.getOrNull()
        return if (holds == true) condition.value else null
    }

    companion object {
        /** The palette's names a chart colors its series with, in turn, the most distinct first. */
        val PALETTE = listOf(TagColor.BLUE, TagColor.ORANGE, TagColor.GREEN, TagColor.PURPLE, TagColor.RED, TagColor.TEAL, TagColor.PINK, TagColor.YELLOW, TagColor.GREY)

        /** The dashes a strokeDash channel gives its categories in turn, in dp; the first solid. */
        val DASHES: List<List<Float>?> = listOf(null, listOf(6f, 4f), listOf(2f, 3f), listOf(8f, 3f, 2f, 3f))
    }
}

/**
 * A curve through points that never overshoots them (monotone cubic interpolation,
 * Fritsch–Carlson): a smoothed line that does not invent a peak between two measures.
 */
internal object Monotone {
    fun curve(points: List<ScenePoint>): List<PathStep> {
        val n = points.size
        if (n < 3) return points.drop(1).map { PathStep.LineTo(it) }
        val dx = (0 until n - 1).map { points[it + 1].x - points[it].x }
        val slopes = (0 until n - 1).map { if (dx[it] == 0f) 0f else (points[it + 1].y - points[it].y) / dx[it] }
        val tangents = FloatArray(n)
        tangents[0] = slopes[0]
        tangents[n - 1] = slopes[n - 2]
        for (i in 1 until n - 1) {
            tangents[i] = if (slopes[i - 1] * slopes[i] <= 0) 0f else (slopes[i - 1] + slopes[i]) / 2
        }
        for (i in 0 until n - 1) {
            if (slopes[i] == 0f) { tangents[i] = 0f; tangents[i + 1] = 0f; continue }
            val a = tangents[i] / slopes[i]
            val b = tangents[i + 1] / slopes[i]
            val h = a * a + b * b
            if (h > 9) {
                val t = 3 / sqrt(h)
                tangents[i] = t * a * slopes[i]
                tangents[i + 1] = t * b * slopes[i]
            }
        }
        return (0 until n - 1).map { i ->
            val p0 = points[i]; val p1 = points[i + 1]; val h = dx[i] / 3
            PathStep.CubicTo(ScenePoint(p0.x + h, p0.y + tangents[i] * h), ScenePoint(p1.x - h, p1.y - tangents[i + 1] * h), p1)
        }
    }
}
