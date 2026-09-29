package com.assistant.core.charts

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.themes.TagColor
import kotlin.math.hypot

/**
 * A chart laid out, ready to draw: the shapes, their place in pixels and their role. The core
 * computes it (ChartSceneBuilder: scales, graduations, stacking, layout, written once); the theme
 * draws it (ThemeContract.ChartScene), each its own way — smooth for the default theme, in pixels
 * and screens for a retro one. Colors stay names of the palette.
 *
 * @property hits What a touch finds, the topmost last
 */
data class ChartScene(val width: Float, val height: Float, val shapes: List<SceneShape>, val hits: List<Hit>) {

    /**
     * What stands at ([x], [y]): the topmost shape holding it, or else the nearest point within
     * [slop] pixels; null when nothing does.
     */
    fun hitAt(x: Float, y: Float, slop: Float): Hit? {
        hits.lastOrNull { it.area != null && it.area.contains(x, y) }?.let { return it }
        return hits.filter { it.point != null }
            .map { it to hypot(it.point!!.x - x, it.point.y - y) }
            .filter { it.second <= slop }
            .minByOrNull { it.second }?.first
    }
}

/** A point, in pixels from the chart's top left corner. */
data class ScenePoint(val x: Float, val y: Float)

/** A rectangle in pixels. */
data class SceneRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** A color of a scene, by meaning: the theme decides what each looks like in its palettes. */
sealed interface SceneColor {
    /** A name of the palette (TagColor). */
    data class Tag(val color: TagColor) : SceneColor

    /** Between two names, at [t] from 0 (the first) to 1 (the second): a quantity colored. */
    data class Mix(val from: TagColor, val to: TagColor, val t: Float) : SceneColor
}

/** What a shape is to the chart: a theme may draw each its own way. */
enum class SceneRole {
    /** A mark of a series */
    MARK,
    /** An axis line */
    AXIS,
    /** A graduation's stroke on an axis */
    TICK,
    /** A grid line across the plot */
    GRID,
    /** A graduation's text */
    AXIS_LABEL,
    /** An axis's title, with its unit */
    AXIS_TITLE,
    /** A small view's title: its category, its field */
    CELL_TITLE,
    /** A legend's title */
    LEGEND_TITLE,
    /** A legend's text */
    LEGEND_LABEL,
    /** A legend's sample of a mark */
    LEGEND_SWATCH,
    /** A value that could not be read, a marked hole */
    HOLE,
    /** A value past fixed bounds, shown at the edge it passes rather than drawn false */
    OUT_OF_BOUNDS
}

/** How a text stands on its point. */
enum class TextAnchor { START, MIDDLE, END }
enum class TextBaseline { TOP, MIDDLE, BOTTOM }

/** One step of a path. */
sealed interface PathStep {
    data class MoveTo(val to: ScenePoint) : PathStep
    data class LineTo(val to: ScenePoint) : PathStep
    data class CubicTo(val control1: ScenePoint, val control2: ScenePoint, val to: ScenePoint) : PathStep
    data object Close : PathStep
}

/** One shape of a scene. A color left null is the theme's own for the role (its ink, its grid). */
sealed interface SceneShape {
    val role: SceneRole

    /** A rectangle: a bar, a rect, a hole, a legend's square. */
    data class Box(val rect: SceneRect, val color: SceneColor?, val opacity: Float, override val role: SceneRole) : SceneShape

    /** A path, stroked or [filled]: a line, an area. */
    data class Path(
        val steps: List<PathStep>,
        val color: SceneColor?,
        val opacity: Float,
        val strokeWidth: Float,
        val dash: List<Float>?,
        val filled: Boolean,
        override val role: SceneRole
    ) : SceneShape

    /** A symbol [size] pixels wide centered on [center]: a point. */
    data class Symbol(val center: ScenePoint, val size: Float, val shape: Shape, val color: SceneColor?, val opacity: Float, val filled: Boolean, override val role: SceneRole) : SceneShape

    /** A slice of a ring, angles in degrees clockwise from the top. */
    data class Arc(val center: ScenePoint, val inner: Float, val outer: Float, val start: Float, val sweep: Float, val color: SceneColor?, val opacity: Float, override val role: SceneRole) : SceneShape

    /** A straight stroke: an axis, a graduation, a grid line, a tick mark. */
    data class Segment(val from: ScenePoint, val to: ScenePoint, val color: SceneColor?, val strokeWidth: Float, val dash: List<Float>?, override val role: SceneRole) : SceneShape

    /** A text, [rotated] a quarter turn counterclockwise when set. */
    data class Label(val text: String, val at: ScenePoint, val anchor: TextAnchor, val baseline: TextBaseline, val color: SceneColor?, override val role: SceneRole, val rotated: Boolean = false) : SceneShape

    /** A small triangle at a plot's edge, pointing [up] or down, for a value past fixed bounds. */
    data class Beyond(val at: ScenePoint, val up: Boolean, val size: Float, val color: SceneColor?) : SceneShape {
        override val role get() = SceneRole.OUT_OF_BOUNDS
    }
}

/**
 * What a touch on a shape finds: the row of a layer's table it stands for, with the table's
 * columns to show it. [area] for a shape with a surface, [point] for a point found by nearness.
 */
data class Hit(val row: Row, val columns: Map<String, FieldDefinition>, val area: SceneRect? = null, val point: ScenePoint? = null)
