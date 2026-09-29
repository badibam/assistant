package com.assistant.tools.chart

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.themes.TagColor
import com.assistant.core.drawing.DrawColor
import com.assistant.core.drawing.DrawShape
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A chart laid out: stacked bars pile at each step, a value that could not be read is a hole
 * touched for its cause, a value past fixed bounds is marked at the edge, a pie's slices share
 * the circle; every mark is found again by a touch.
 */
class ChartSceneBuilderTest {

    private val zone = ZoneId.of("Europe/Paris")
    private fun day(d: Int) = LocalDateTime.of(2026, 9, d, 0, 0).atZone(zone).toInstant().toEpochMilli()

    private val text = object : ChartText {
        override fun width(text: String) = text.length * 7f
        override val height = 14f
        override fun value(field: FieldDefinition, value: Any) = value.toString()
        override fun instant(instant: Long, step: ChartTicks.CalendarStep) = instant.toString()
        override fun number(value: Double, decimals: Int) = "%.${decimals}f".format(value)
        override fun shared(key: String) = "$key %1\$s"
        override fun own(key: String) = "$key %1\$s"
    }

    private fun builder() = ChartSceneBuilder(ChartMetrics(1f), text, zone, "monday", day(30))
    private val timestamp = FieldDefinition("timestamp", "Day", null, FieldType.DATETIME, false, null)
    private fun numeric(name: String) = FieldDefinition(name, name, null, FieldType.NUMERIC, false, null)
    private val period = """"period": {"start": 0}"""

    private fun spec(layers: String) = ChartSpec.of(JSONObject("""{$period, "layer": [$layers]}"""), { it }, { it })

    /** Three days, each a step: two columns folded into kinds. */
    private fun kcal(failedOn: Int? = null) = ChartTable(
        mapOf("timestamp" to timestamp, "kind" to FieldDefinition("kind", "Kind", null, FieldType.CHOICE, false,
            mapOf("options" to listOf(mapOf("value" to "food"), mapOf("value" to "empty")))), "kcal" to numeric("kcal")),
        (1..3).flatMap { d ->
            val span = day(d) to day(d + 1) - 1
            listOf(
                Row(mapOf("timestamp" to Cell.Value(day(d)), "kind" to Cell.Value("food"),
                    "kcal" to if (d == failedOn) Cell.Failed("no entry", listOf("e$d")) else Cell.Value(1500.0)), span),
                Row(mapOf("timestamp" to Cell.Value(day(d)), "kind" to Cell.Value("empty"), "kcal" to Cell.Value(300.0)), span)
            )
        }
    )

    private val stackedBars = """{"source": "grid", "step": "day", "columns": [{"name": "a", "term": {"variable": "v"}}],
        "mark": {"type": "bar"}, "encoding": {"x": {"field": "timestamp"}, "y": {"field": "kcal", "stack": "zero"},
        "color": {"field": "kind", "scale": {"range": ["GREEN", "RED"]}}}}"""

    /** The marks: shapes in a color of the palette, the theme's inks being axes and texts. */
    private fun marks(layout: ChartLayout) = layout.drawing.shapes.filter { shape ->
        val color = when (shape) {
            is DrawShape.Box -> shape.color.takeIf { !shape.missing }
            is DrawShape.Path -> shape.color
            is DrawShape.Symbol -> shape.color
            is DrawShape.Arc -> shape.color
            else -> null
        }
        color is DrawColor.Palette || color is DrawColor.Mix
    }
    private fun labels(layout: ChartLayout) = layout.drawing.shapes.filterIsInstance<DrawShape.Label>().map { it.text }

    @Test
    fun `stacked bars pile at each step, each category in its color`() {
        val scene = builder().build(spec(stackedBars), listOf(kcal()), day(1) to day(4) - 1, 400f)
        // The bars, and the legend's two squares after them
        val bars = marks(scene).filterIsInstance<DrawShape.Box>().take(6)
        assertEquals(8, marks(scene).filterIsInstance<DrawShape.Box>().size)
        val (food, empty) = bars.take(2)
        assertEquals(DrawColor.Palette(TagColor.GREEN), food.color)
        assertEquals(DrawColor.Palette(TagColor.RED), empty.color)
        // The second sits on the first: its bottom is the first's top
        assertEquals(food.rect.top, empty.rect.bottom, 0.01f)
        assertEquals(food.rect.left, empty.rect.left, 0.01f)
        // Food's 1500 is five times empty's 300
        assertEquals(5f, (food.rect.bottom - food.rect.top) / (empty.rect.bottom - empty.rect.top), 0.01f)
        // Its legend names both kinds
        assertEquals(1, labels(scene).count { it == "food" })
        assertEquals(1, labels(scene).count { it == "empty" })
        // A touch on a bar finds its row
        val hit = scene.hitAt((food.rect.left + food.rect.right) / 2, (food.rect.top + food.rect.bottom) / 2, 24f)
        assertEquals("food", hit!!.row.value("kind"))
    }

    @Test
    fun `a value that could not be read is a hole, touched for its cause`() {
        val scene = builder().build(spec(stackedBars), listOf(kcal(failedOn = 2)), day(1) to day(4) - 1, 400f)
        val hole = scene.drawing.shapes.filterIsInstance<DrawShape.Box>().single { it.missing }
        val hit = scene.hitAt((hole.rect.left + hole.rect.right) / 2, hole.rect.top + 2, 24f)
        assertEquals("no entry", hit!!.row.failure("kcal")!!.message)
        // Not drawn as a bar: five of the six remain, beside the legend's two squares
        assertEquals(7, marks(scene).filterIsInstance<DrawShape.Box>().size)
    }

    @Test
    fun `a value past fixed bounds is marked at the edge it passes, not drawn against it`() {
        val weight = ChartTable(mapOf("timestamp" to timestamp, "w" to numeric("w")), listOf(
            Row(mapOf("timestamp" to Cell.Value(day(1)), "w" to Cell.Value(72.0))),
            Row(mapOf("timestamp" to Cell.Value(day(2)), "w" to Cell.Value(95.0))),
            Row(mapOf("timestamp" to Cell.Value(day(3)), "w" to Cell.Value(71.0)))
        ))
        val layer = """{"source": "entries", "selection": {"target": {"kind": "TOOL_INSTANCE", "id": "t"}}, "mark": {"type": "point"},
            "encoding": {"x": {"field": "timestamp"}, "y": {"field": "w", "scale": {"domain": [60, 80]}}}}"""
        val scene = builder().build(spec(layer), listOf(weight), day(1) to day(3), 400f)
        assertEquals(2, marks(scene).filterIsInstance<DrawShape.Symbol>().size)
        val beyond = scene.drawing.shapes.filterIsInstance<DrawShape.Arrowhead>().single()
        assertTrue(beyond.up)
        // The bound passed: the top of the plot, where 80 stands
        val top = scene.drawing.shapes.filterIsInstance<DrawShape.Label>().single { it.text == "80" }.at.y
        assertEquals(top, beyond.at.y, 0.01f)
    }

    @Test
    fun `a line breaks where a value is missing`() {
        val table = ChartTable(mapOf("timestamp" to timestamp, "w" to numeric("w")), (1..5).map { d ->
            Row(mapOf("timestamp" to Cell.Value(day(d)), "w" to Cell.Value(if (d == 3) null else 70.0 + d)))
        })
        val layer = """{"source": "entries", "selection": {"target": {"kind": "TOOL_INSTANCE", "id": "t"}}, "mark": {"type": "line"},
            "encoding": {"x": {"field": "timestamp"}, "y": {"field": "w", "scale": {"zero": false}}}}"""
        val scene = builder().build(spec(layer), listOf(table), day(1) to day(5), 400f)
        assertEquals(2, marks(scene).filterIsInstance<DrawShape.Path>().size)
    }

    @Test
    fun `a pie's slices share the circle in the colors' order`() {
        val table = ChartTable(mapOf("macro" to FieldDefinition("macro", "Macro", null, FieldType.TEXT, false, null), "g" to numeric("g")), listOf(
            Row(mapOf("macro" to Cell.Value("prot"), "g" to Cell.Value(100.0))),
            Row(mapOf("macro" to Cell.Value("carb"), "g" to Cell.Value(300.0)))
        ))
        val layer = """{"source": "entries", "selection": {"target": {"kind": "TOOL_INSTANCE", "id": "t"}}, "mark": {"type": "arc"},
            "encoding": {"theta": {"field": "g"}, "color": {"field": "macro"}}}"""
        val scene = builder().build(spec(layer), listOf(table), null to null, 400f)
        val arcs = marks(scene).filterIsInstance<DrawShape.Arc>()
        assertEquals(listOf(90f, 270f), arcs.map { it.sweep })
        assertEquals(90f, arcs[1].start, 0.01f)
        assertNotNull(scene.hitAt(arcs[0].center.x + 30f, arcs[0].center.y - 60f, 40f))
        assertNull(scene.hitAt(0f, scene.drawing.height, 1f))
    }

    @Test
    fun `a strip draws the marks alone at the height given`() {
        val layout = builder().build(spec(stackedBars), listOf(kcal()), day(1) to day(4) - 1, 400f, ChartDetail.STRIP, height = 60f)
        assertEquals(60f, layout.drawing.height, 0.01f)
        assertEquals(6, marks(layout).filterIsInstance<DrawShape.Box>().size)
        // No graduation, no title, no legend: not a word
        assertTrue(labels(layout).isEmpty())
    }

    @Test
    fun `a reduced chart keeps a few graduations, no legend, and its first view only`() {
        val views = ChartSpec.of(JSONObject("""{$period, "composition": "vconcat", "vconcat": [{"title": "A", "layer": [$stackedBars]}, {"title": "B", "layer": [$stackedBars]}]}"""), { it }, { it })
        val layout = builder().build(views, listOf(kcal(), kcal()), day(1) to day(4) - 1, 400f, ChartDetail.REDUCED, height = 180f)
        assertEquals(6, marks(layout).filterIsInstance<DrawShape.Box>().size)
        assertTrue("A" !in labels(layout) && "food" !in labels(layout))
        val left = layout.drawing.shapes.filterIsInstance<DrawShape.Label>().filter { it.anchor == com.assistant.core.drawing.TextAnchor.END }
        assertTrue(left.size in 2..4)
    }
}

