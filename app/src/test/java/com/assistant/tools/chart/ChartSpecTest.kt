package com.assistant.tools.chart

import com.assistant.core.selection.TimePoint
import com.assistant.core.terms.Term
import com.assistant.core.themes.TagColor
import com.assistant.core.ui.components.PeriodType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A chart's config reads into what it draws: its period, how its views compose, each layer's
 * source, transforms, mark and channels; what does not read is named, never guessed.
 */
class ChartSpecTest {

    private fun read(json: String) = ChartSpec.of(JSONObject(json), { it }) { key -> key + ":%1\$s:%2\$s:%3\$s" }

    private val period = """"period": {"start": {"relative": {"unit": "DAY", "offset": -29, "edge": "START"}}, "end": {"relative": "NOW"}}"""

    /** The first chart of a food tracking: kcal of each day, stacked, the goal in a line, the weight on the right. */
    private val food = """{
        $period,
        "layer": [
            {"source": "grid", "step": "day", "columns": [
                {"name": "kcal_aliments", "term": {"variable": "v1"}},
                {"name": "kcal_vides", "term": {"variable": "v2"}}],
             "transform": [{"fold": ["kcal_aliments", "kcal_vides"], "as": ["kind", "kcal"]}],
             "mark": {"type": "bar"},
             "encoding": {"x": {"field": "timestamp"}, "y": {"field": "kcal", "stack": "zero"}, "color": {"field": "kind", "scale": {"range": ["GREEN", "RED"]}}}},
            {"source": "grid", "step": "day", "columns": [{"name": "objectif", "term": {"variable": "v3"}}],
             "mark": {"type": "line", "interpolate": "step-after", "strokeDash": [4, 4]},
             "encoding": {"x": {"field": "timestamp"}, "y": {"field": "objectif"}}},
            {"source": "grid", "step": "day", "columns": [{"name": "poids", "term": {"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "t1"}}, "field": "data.value", "reduction": "LAST"}}}],
             "mark": {"type": "line", "point": true},
             "encoding": {"x": {"field": "timestamp"}, "y": {"field": "poids", "scale": {"zero": false}, "axis": {"orient": "right"}}}}
        ]
    }"""

    @Test
    fun `a layered chart reads its period, its sources and its channels`() {
        val spec = read(food)
        assertEquals(TimePoint.Now, spec.period.end)
        assertTrue(spec.composition is Composition.Single)
        assertEquals(3, spec.layers.size)
        val bars = spec.layers[0]
        assertEquals(PeriodType.DAY, (bars.source as Source.Grid).step)
        assertEquals(Term.Variable("v1"), (bars.source as Source.Grid).columns[0].term)
        assertEquals(Transform.Fold(listOf("kcal_aliments", "kcal_vides"), "kind", "kcal"), bars.transforms.single())
        assertEquals(Stack.ZERO, bars.channel(Channel.Y)!!.stack)
        assertEquals(listOf(TagColor.GREEN, TagColor.RED), bars.channel(Channel.COLOR)!!.scale.range)
        assertEquals(Interpolate.STEP_AFTER, spec.layers[1].mark.interpolate)
        assertEquals(listOf(4f, 4f), spec.layers[1].mark.strokeDash)
        val weight = spec.layers[2]
        assertEquals(Orient.RIGHT, weight.channel(Channel.Y)!!.axis.orient)
        assertEquals(false, weight.channel(Channel.Y)!!.scale.zero)
        assertTrue(weight.mark.point)
    }

    @Test
    fun `views compose one above the other, per category, per column`() {
        val layer = """{"source": "entries", "selection": {"target": {"kind": "TOOL_INSTANCE", "id": "t1"}}, "mark": {"type": "point"}, "encoding": {"x": {"field": "timestamp"}, "y": {"field": "repeat"}}}"""
        val concat = read("""{$period, "composition": "vconcat", "vconcat": [{"layer": [$layer]}, {"layer": [$layer]}]}""").composition as Composition.Concat
        assertEquals(ConcatDirection.VERTICAL, concat.direction)
        assertEquals(2, concat.children.size)
        val facet = read("""{$period, "composition": "facet", "facet": {"field": "data.mood"}, "columns": 2, "layer": [$layer]}""").composition as Composition.Facet
        assertEquals("data.mood", facet.field)
        assertEquals(2, facet.columns)
        val repeat = read("""{$period, "composition": "repeat", "repeat": ["data.a", "data.b"], "layer": [$layer]}""").composition as Composition.Repeat
        assertEquals(listOf("data.a", "data.b"), repeat.fields)
    }

    @Test
    fun `what does not read is named`() {
        val layer = """{"source": "entries", "selection": {"target": {"kind": "TOOL_INSTANCE", "id": "t1"}}, "mark": {"type": "rule"}}"""
        val error = assertThrows(IllegalArgumentException::class.java) { read("""{$period, "layer": [$layer]}""") }
        assertTrue(error.message!!.startsWith("error_option:mark.type:rule"))
        assertThrows(IllegalArgumentException::class.java) { read("""{"layer": []}""") }
        val color = """{"source": "entries", "selection": {"target": {"kind": "TOOL_INSTANCE", "id": "t1"}}, "mark": {"type": "bar"}, "encoding": {"color": {"value": "#ff0000"}}}"""
        assertThrows(IllegalArgumentException::class.java) { read("""{$period, "layer": [$color]}""") }
        val transform = """{"source": "entries", "selection": {"target": {"kind": "TOOL_INSTANCE", "id": "t1"}}, "transform": [{}], "mark": {"type": "bar"}}"""
        assertThrows(IllegalArgumentException::class.java) { read("""{$period, "layer": [$transform]}""") }
    }
}
