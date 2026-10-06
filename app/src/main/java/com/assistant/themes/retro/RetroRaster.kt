package com.assistant.themes.retro

import com.assistant.core.drawing.DrawColor
import com.assistant.core.drawing.DrawPoint
import com.assistant.core.drawing.DrawRect
import com.assistant.core.drawing.DrawShape
import com.assistant.core.drawing.Drawing
import com.assistant.core.drawing.PathStep
import com.assistant.core.drawing.SymbolShape
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A drawing (Drawing) put down in the retro theme's pixels, [scale] screen pixels each: every
 * shape on whole pixels, in the colors [color] gives, never blended (pixel-ui: the softness is
 * painted, never filtered).
 *
 * - An opacity is a screen of dots (TRAME): of every four by four pixels, about as many are lit
 *   as the opacity says, the others left as they were.
 * - What is missing is a hatch of dots, its edge dotted.
 * - A stroke is one or two pixels thick, in steps; its dashes are counted in pixels.
 * - A symbol is drawn on an odd number of pixels round a middle one, the same shape at every place.
 * - A slice of a ring leaves a pixel of ground along the side it starts from.
 *
 * Texts (DrawShape.Label) are not put down here: the theme writes them over in its font, on whole pixels.
 * Nothing here knows Android, so the rules are tested as they are.
 */
internal class RetroRaster(drawing: Drawing, private val scale: Int, private val color: (DrawColor) -> Int) {

    /** The pixels across and down: the drawing's size, the last one cut. */
    val width: Int = ceil(drawing.width / scale).toInt().coerceAtLeast(1)
    val height: Int = ceil(drawing.height / scale).toInt().coerceAtLeast(1)

    /** Each pixel's ARGB, row by row; 0 where nothing is drawn. */
    val pixels = IntArray(width * height)

    init {
        drawing.shapes.forEach { shape ->
            when (shape) {
                is DrawShape.Box -> if (shape.missing) missing(shape.rect, color(shape.color)) else box(shape.rect, color(shape.color), shape.opacity)
                is DrawShape.Path -> if (shape.filled) fill(polygons(shape.steps), color(shape.color), shape.opacity)
                    else polygons(shape.steps).forEach { stroke(it, color(shape.color), shape.opacity, shape.strokeWidth, shape.dash) }
                is DrawShape.Symbol -> symbol(shape.center, shape.size, shape.shape, shape.filled, color(shape.color), shape.opacity)
                is DrawShape.Arc -> arc(shape, color(shape.color))
                is DrawShape.Segment -> stroke(listOf(px(shape.from), px(shape.to)), color(shape.color), 1f, shape.strokeWidth, shape.dash)
                is DrawShape.Arrowhead -> arrowhead(shape.at, shape.size, shape.up, color(shape.color))
                is DrawShape.Label -> Unit
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Pixels
    // ---------------------------------------------------------------------------------------

    /** A point of the drawing, in pixels (fractional). */
    private fun px(p: DrawPoint) = DrawPoint(p.x / scale, p.y / scale)

    /** A length of the drawing as whole pixels, one at least. */
    private fun whole(length: Float) = (length / scale).roundToInt().coerceAtLeast(1)

    /** Lights [x], [y] if the screen of [opacity] lights it there. */
    private fun plot(x: Int, y: Int, argb: Int, opacity: Float = 1f) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        if (!lit(x, y, opacity)) return
        pixels[y * width + x] = argb
    }

    /** A rectangle, each side on the pixel edge nearest it, one pixel across at least. */
    private fun cells(rect: DrawRect): IntArray {
        val left = (rect.left / scale).roundToInt()
        val top = (rect.top / scale).roundToInt()
        val right = max((rect.right / scale).roundToInt(), left + 1)
        val bottom = max((rect.bottom / scale).roundToInt(), top + 1)
        return intArrayOf(left, top, right, bottom)
    }

    private fun box(rect: DrawRect, argb: Int, opacity: Float) {
        val (left, top, right, bottom) = cells(rect)
        for (y in top until bottom) for (x in left until right) plot(x, y, argb, opacity)
    }

    /** A hatch of single dots on the diagonals, every fourth, and the edge dotted every other pixel. */
    private fun missing(rect: DrawRect, argb: Int) {
        val (left, top, right, bottom) = cells(rect)
        for (y in top until bottom) for (x in left until right) {
            val edge = x == left || x == right - 1 || y == top || y == bottom - 1
            if (if (edge) (x + y) % 2 == 0 else (x + y) % 4 == 0) plot(x, y, argb)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Strokes and fills
    // ---------------------------------------------------------------------------------------

    /** The subpaths of [steps] as broken lines in pixels, a curve cut in short straight pieces. */
    private fun polygons(steps: List<PathStep>): List<List<DrawPoint>> {
        val all = mutableListOf<List<DrawPoint>>()
        var current = mutableListOf<DrawPoint>()
        steps.forEach { step ->
            when (step) {
                is PathStep.MoveTo -> {
                    if (current.size > 1) all.add(current)
                    current = mutableListOf(px(step.to))
                }
                is PathStep.LineTo -> current.add(px(step.to))
                is PathStep.CubicTo -> {
                    val p0 = current.lastOrNull() ?: px(step.to)
                    val c1 = px(step.control1); val c2 = px(step.control2); val p3 = px(step.to)
                    // A piece every two pixels or so: finer would only repeat the same pixels
                    val pieces = ceil((hypot(c1.x - p0.x, c1.y - p0.y) + hypot(c2.x - c1.x, c2.y - c1.y) + hypot(p3.x - c2.x, p3.y - c2.y)) / 2f).toInt().coerceIn(1, 64)
                    for (i in 1..pieces) {
                        val t = i.toFloat() / pieces; val u = 1 - t
                        current.add(DrawPoint(
                            u * u * u * p0.x + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * p3.x,
                            u * u * u * p0.y + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * p3.y
                        ))
                    }
                }
                PathStep.Close -> current.firstOrNull()?.let { current.add(it) }
            }
        }
        if (current.size > 1) all.add(current)
        return all
    }

    /**
     * A broken line through [points], [strokeWidth] as whole pixels thick, in steps from pixel to
     * pixel; [dash] counted in pixels along it, the count going on from one piece to the next.
     */
    private fun stroke(points: List<DrawPoint>, argb: Int, opacity: Float, strokeWidth: Float, dash: List<Float>?) {
        val thickness = whole(strokeWidth)
        val pattern = dash?.map { whole(it) }?.takeIf { it.isNotEmpty() }
        var walked = 0
        var last: Pair<Int, Int>? = null
        points.zipWithNext { a, b ->
            line(floor(a.x).toInt(), floor(a.y).toInt(), floor(b.x).toInt(), floor(b.y).toInt()) { x, y ->
                // A joint is the end of one piece and the start of the next: counted once
                if (last == x to y) return@line
                last = x to y
                val on = pattern == null || dashOn(pattern, walked)
                walked++
                if (on) for (dy in 0 until thickness) for (dx in 0 until thickness) {
                    plot(x + dx - (thickness - 1) / 2, y + dy - (thickness - 1) / 2, argb, opacity)
                }
            }
        }
    }

    /** Whether the [step]th pixel of a dashed stroke is drawn: the pattern's even places are. */
    private fun dashOn(pattern: List<Int>, step: Int): Boolean {
        var rest = step % pattern.sum()
        pattern.forEachIndexed { i, length ->
            if (rest < length) return i % 2 == 0
            rest -= length
        }
        return true
    }

    /** Bresenham's line from [x0], [y0] to [x1], [y1], both ends included. */
    private inline fun line(x0: Int, y0: Int, x1: Int, y1: Int, visit: (Int, Int) -> Unit) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var error = dx + dy
        while (true) {
            visit(x, y)
            if (x == x1 && y == y1) break
            val twice = 2 * error
            if (twice >= dy) { error += dy; x += sx }
            if (twice <= dx) { error += dx; y += sy }
        }
    }

    /** The inside of [polygons], even-odd: every pixel whose middle is inside. */
    private fun fill(polygons: List<List<DrawPoint>>, argb: Int, opacity: Float) {
        val edges = polygons.flatMap { poly -> (poly + poly.first()).zipWithNext() }
        if (edges.isEmpty()) return
        val top = max(0, floor(edges.minOf { min(it.first.y, it.second.y) }).toInt())
        val bottom = min(height - 1, ceil(edges.maxOf { max(it.first.y, it.second.y) }).toInt())
        for (y in top..bottom) {
            val middle = y + 0.5f
            val crossings = edges.mapNotNull { (a, b) ->
                if ((a.y <= middle) == (b.y <= middle)) null
                else a.x + (middle - a.y) / (b.y - a.y) * (b.x - a.x)
            }.sorted()
            for (i in 0 until crossings.size - 1 step 2) {
                for (x in ceil(crossings[i] - 0.5f).toInt() until ceil(crossings[i + 1] - 0.5f).toInt()) plot(x, y, argb, opacity)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Symbols, arrowheads, slices
    // ---------------------------------------------------------------------------------------

    /** [size] as an odd number of pixels, three at least, so a symbol has a middle pixel. */
    private fun odd(size: Float): Int = whole(size).let { if (it % 2 == 0) it + 1 else it }.coerceAtLeast(3)

    private fun symbol(center: DrawPoint, size: Float, shape: SymbolShape, filled: Boolean, argb: Int, opacity: Float) {
        val half = odd(size) / 2
        val cx = floor(center.x / scale).toInt(); val cy = floor(center.y / scale).toInt()
        val inside = { dx: Int, dy: Int -> abs(dx) <= half && abs(dy) <= half && inSymbol(shape, half, dx, dy) }
        for (dy in -half..half) for (dx in -half..half) {
            if (!inside(dx, dy)) continue
            // Hollow, only the pixels with a side out of the shape; a cross is lines already
            val edge = !inside(dx - 1, dy) || !inside(dx + 1, dy) || !inside(dx, dy - 1) || !inside(dx, dy + 1)
            if (filled || shape == SymbolShape.CROSS || edge) plot(cx + dx, cy + dy, argb, opacity)
        }
    }

    /** Whether [dx], [dy] from the middle is in [shape] of [half] pixels each side. */
    private fun inSymbol(shape: SymbolShape, half: Int, dx: Int, dy: Int): Boolean = when (shape) {
        // h² + h keeps the corners off: the round of a five-pixel dot, of a seven
        SymbolShape.CIRCLE -> dx * dx + dy * dy <= half * half + half
        SymbolShape.SQUARE -> true
        SymbolShape.DIAMOND -> abs(dx) + abs(dy) <= half
        // Pointing up: a row widens by a pixel each side every two rows down
        SymbolShape.TRIANGLE -> abs(dx) <= (dy + half) / 2
        SymbolShape.CROSS -> abs(dx) == abs(dy)
    }

    /** A filled triangle [size] wide round [at], its point up or down. */
    private fun arrowhead(at: DrawPoint, size: Float, up: Boolean, argb: Int) {
        val half = odd(size) / 2
        val cx = floor(at.x / scale).toInt(); val cy = floor(at.y / scale).toInt()
        for (dy in -half..half) for (dx in -half..half) {
            if (inSymbol(SymbolShape.TRIANGLE, half, dx, if (up) dy else -dy)) plot(cx + dx, cy + dy, argb)
        }
    }

    /**
     * A slice: every pixel whose middle lies between its radii and its angles, but those within a
     * pixel of the side it starts from, so two slices are parted by a pixel of ground wherever
     * their common side falls (half a pixel off each side leaves none when it falls between two rows).
     */
    private fun arc(shape: DrawShape.Arc, argb: Int) {
        val cx = shape.center.x / scale; val cy = shape.center.y / scale
        val inner = shape.inner / scale; val outer = shape.outer / scale
        val whole = shape.sweep >= 359.99f
        for (y in max(0, floor(cy - outer).toInt())..min(height - 1, ceil(cy + outer).toInt())) {
            for (x in max(0, floor(cx - outer).toInt())..min(width - 1, ceil(cx + outer).toInt())) {
                val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
                val distance = hypot(dx, dy)
                if (distance < inner || distance > outer) continue
                if (!whole) {
                    // Degrees clockwise from the top, from the slice's start
                    val angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                    val from = (((angle - shape.start) % 360f) + 360f) % 360f
                    if (from > shape.sweep) continue
                    if (from < 90f && distance * sin(Math.toRadians(from.toDouble())).toFloat() < 1f) continue
                }
                plot(x, y, argb, shape.opacity)
            }
        }
    }

    companion object {
        /**
         * The order a four-by-four screen lights its pixels in (Bayer's): any count of them is
         * spread evenly, never in a clump.
         */
        private val TRAME = intArrayOf(
            0, 8, 2, 10,
            12, 4, 14, 6,
            3, 11, 1, 9,
            15, 7, 13, 5,
        )

        /** Whether the screen of [opacity] lights [x], [y]: about opacity × 16 of every sixteen. */
        fun lit(x: Int, y: Int, opacity: Float): Boolean =
            opacity >= 1f || (TRAME[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f < opacity
    }
}
