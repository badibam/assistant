package com.assistant.themes.retro

import com.assistant.core.drawing.DrawColor
import com.assistant.core.drawing.DrawPoint
import com.assistant.core.drawing.DrawRect
import com.assistant.core.drawing.DrawShape
import com.assistant.core.drawing.Drawing
import com.assistant.core.drawing.InkLevel
import com.assistant.core.drawing.PathStep
import com.assistant.core.drawing.SymbolShape
import com.assistant.core.themes.TagColor
import com.assistant.tools.chart.ChartMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The retro theme's drawings in pixels: bars of one width drawn equal, an opacity a screen of
 * dots and never a blend, two slices parted by ground, a dot round and the same everywhere.
 */
class RetroRasterTest {

    private val scale = 3
    private val colors = mapOf<DrawColor, Int>(
        DrawColor.Palette(TagColor.BLUE) to 0xFF0000FF.toInt(),
        DrawColor.Palette(TagColor.RED) to 0xFFFF0000.toInt(),
        DrawColor.Ink(InkLevel.MEDIUM) to 0xFF808080.toInt(),
        DrawColor.Ink(InkLevel.FAINT) to 0xFFC0C0C0.toInt(),
    )
    private val blue = DrawColor.Palette(TagColor.BLUE)
    private val red = DrawColor.Palette(TagColor.RED)

    private fun raster(width: Float, height: Float, vararg shapes: DrawShape) =
        RetroRaster(Drawing(width, height, shapes.toList()), scale) { colors.getValue(it) }

    private fun RetroRaster.at(x: Int, y: Int) = pixels[y * width + x]

    /** The columns of row [y] that are lit, in runs: each run's length. */
    private fun RetroRaster.runs(y: Int): List<Int> {
        val runs = mutableListOf<Int>()
        var length = 0
        for (x in 0 until width) {
            if (at(x, y) != 0) length++ else if (length > 0) { runs.add(length); length = 0 }
        }
        if (length > 0) runs.add(length)
        return runs
    }

    @Test
    fun `bars of one width at any place are drawn equal`() {
        val metrics = ChartMetrics(density = 3.5f, unit = scale.toFloat())
        // A time axis of 31 days over 1000 screen pixels: each day 32.26 pixels, starting anywhere
        val step = 1000f / 31
        val bars = (0 until 31).map { i ->
            val (left, right) = metrics.across(i * step + 2f, (i + 1) * step - 2f)
            DrawShape.Box(DrawRect(left, 10f, right, 100f), blue)
        }
        val r = raster(1010f, 110f, *bars.toTypedArray())
        val runs = r.runs(20)
        assertEquals(31, runs.size)
        assertEquals("every bar the same width: $runs", 1, runs.distinct().size)
    }

    @Test
    fun `an opacity lights its share of every four by four pixels, never a blend`() {
        val r = raster(12f * scale, 12f * scale, DrawShape.Box(DrawRect(0f, 0f, 12f * scale, 12f * scale), blue, opacity = 0.7f))
        for (by in 0 until 3) for (bx in 0 until 3) {
            var lit = 0
            for (y in 0 until 4) for (x in 0 until 4) {
                val p = r.at(bx * 4 + x, by * 4 + y)
                assertTrue(p == 0 || p == colors.getValue(blue))
                if (p != 0) lit++
            }
            assertEquals(11, lit)
        }
    }

    @Test
    fun `every pixel is a color given or nothing`() {
        val r = raster(
            60f * scale, 40f * scale,
            DrawShape.Path(listOf(PathStep.MoveTo(DrawPoint(0f, 0f)), PathStep.CubicTo(DrawPoint(40f, 100f), DrawPoint(80f, -20f), DrawPoint(170f, 110f))), blue, 0.5f, 7f, listOf(9f, 6f), filled = false),
            DrawShape.Path(listOf(PathStep.MoveTo(DrawPoint(0f, 110f)), PathStep.LineTo(DrawPoint(90f, 30f)), PathStep.LineTo(DrawPoint(170f, 110f)), PathStep.Close), red, 0.7f, 0f, null, filled = true),
            DrawShape.Box(DrawRect(10f, 10f, 50f, 100f), DrawColor.Ink(InkLevel.MEDIUM), missing = true),
            DrawShape.Symbol(DrawPoint(90f, 60f), 21f, SymbolShape.DIAMOND, blue, 1f, filled = false),
            DrawShape.Arc(DrawPoint(120f, 60f), 0f, 50f, 0f, 120f, red, 0.8f),
        )
        val allowed = colors.values.toSet() + 0
        assertTrue(r.pixels.all { it in allowed })
    }

    @Test
    fun `two slices are parted by ground along their common side`() {
        val center = 30f * scale
        val r = raster(60f * scale, 60f * scale,
            DrawShape.Arc(DrawPoint(center, center), 0f, 25f * scale, 0f, 90f, blue, 1f),
            DrawShape.Arc(DrawPoint(center, center), 0f, 25f * scale, 90f, 270f, red, 1f))
        // The side at 90°: the row through the middle, right of it, never blue against red
        for (x in 33 until 54) {
            val above = r.at(x, 29); val below = r.at(x, 30)
            assertTrue("at $x: $above / $below", !(above == colors.getValue(blue) && below == colors.getValue(red)))
        }
        assertTrue(r.pixels.count { it == colors.getValue(blue) } > 300)
    }

    @Test
    fun `a dot is round on an odd number of pixels, and the same wherever it falls`() {
        // 6 dp at 3.5: 21 screen pixels, seven drawing pixels
        val first = raster(40f * scale, 20f * scale, DrawShape.Symbol(DrawPoint(10f * scale + 1f, 10f * scale + 2f), 21f, SymbolShape.CIRCLE, blue, 1f, filled = true))
        val second = raster(40f * scale, 20f * scale, DrawShape.Symbol(DrawPoint(30f * scale + 2.5f, 10f * scale), 21f, SymbolShape.CIRCLE, blue, 1f, filled = true))
        assertEquals(listOf(3), first.runs(7))
        assertEquals(listOf(7), first.runs(10))
        assertEquals(first.pixels.count { it != 0 }, second.pixels.count { it != 0 })
        for (y in 7..13) for (dx in -3..3) assertEquals(first.at(10 + dx, y) != 0, second.at(30 + dx, y) != 0)
    }

    @Test
    fun `a graduation in the faint ink is dotted, an axis in the medium one is not`() {
        val r = raster(20f * scale, 10f * scale,
            DrawShape.Segment(DrawPoint(0f, 2f * scale), DrawPoint(20f * scale, 2f * scale), DrawColor.Ink(InkLevel.FAINT), 3.5f, null),
            DrawShape.Segment(DrawPoint(0f, 6f * scale), DrawPoint(20f * scale, 6f * scale), DrawColor.Ink(InkLevel.MEDIUM), 3.5f, null))
        assertEquals(List(10) { 1 }, r.runs(2))
        assertEquals(listOf(20), r.runs(6))
    }
}
