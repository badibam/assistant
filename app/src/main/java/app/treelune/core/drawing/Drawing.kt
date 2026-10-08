package app.treelune.core.drawing

import app.treelune.core.themes.TagColor

/**
 * A drawing laid out by whoever makes it (a chart, a preview on a tile), ready for the theme to
 * draw (ThemeContract.Drawing): shapes placed in pixels, their colors given by meaning, never by
 * value. The theme decides what each looks like — smooth for the default theme, in pixels and
 * screens for a retro one — and knows nothing of what the drawing stands for.
 */
data class Drawing(val width: Float, val height: Float, val shapes: List<DrawShape>)

/** A point, in pixels from the drawing's top left corner. */
data class DrawPoint(val x: Float, val y: Float)

/** A rectangle in pixels. */
data class DrawRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** How strongly the theme's own ink shows: titles, texts and strokes, lines behind the rest. */
enum class InkLevel { STRONG, MEDIUM, FAINT }

/** A color by meaning: the theme decides what each looks like in its palettes. */
sealed interface DrawColor {
    /** A name of the palette (TagColor). */
    data class Palette(val color: TagColor) : DrawColor

    /** Between two names, at [t] from 0 (the first) to 1 (the second): a quantity colored. */
    data class Mix(val from: TagColor, val to: TagColor, val t: Float) : DrawColor

    /** The theme's own ink, at [level]. */
    data class Ink(val level: InkLevel) : DrawColor
}

/** The shape of a symbol. */
enum class SymbolShape { CIRCLE, SQUARE, TRIANGLE, DIAMOND, CROSS }

/** How a text stands on its point. */
enum class TextAnchor { START, MIDDLE, END }
enum class TextBaseline { TOP, MIDDLE, BOTTOM }

/** One step of a path. */
sealed interface PathStep {
    data class MoveTo(val to: DrawPoint) : PathStep
    data class LineTo(val to: DrawPoint) : PathStep
    data class CubicTo(val control1: DrawPoint, val control2: DrawPoint, val to: DrawPoint) : PathStep
    data object Close : PathStep
}

/** One shape of a drawing. */
sealed interface DrawShape {

    /** A rectangle, filled; [missing] marks what could not be known, hatched by the default theme. */
    data class Box(val rect: DrawRect, val color: DrawColor, val opacity: Float = 1f, val missing: Boolean = false) : DrawShape

    /** A path, stroked or [filled]. */
    data class Path(val steps: List<PathStep>, val color: DrawColor, val opacity: Float, val strokeWidth: Float, val dash: List<Float>?, val filled: Boolean) : DrawShape

    /** A symbol [size] pixels wide centered on [center]. */
    data class Symbol(val center: DrawPoint, val size: Float, val shape: SymbolShape, val color: DrawColor, val opacity: Float, val filled: Boolean) : DrawShape

    /** A slice of a ring, angles in degrees clockwise from the top. */
    data class Arc(val center: DrawPoint, val inner: Float, val outer: Float, val start: Float, val sweep: Float, val color: DrawColor, val opacity: Float) : DrawShape

    /** A straight stroke. */
    data class Segment(val from: DrawPoint, val to: DrawPoint, val color: DrawColor, val strokeWidth: Float, val dash: List<Float>?) : DrawShape

    /** A text, in the style the theme gives drawings (ThemeContract.drawingTextStyle). */
    data class Label(val text: String, val at: DrawPoint, val anchor: TextAnchor, val baseline: TextBaseline, val color: DrawColor) : DrawShape

    /** A small triangle [size] pixels wide centered on [at], pointing [up] or down. */
    data class Arrowhead(val at: DrawPoint, val up: Boolean, val size: Float, val color: DrawColor) : DrawShape
}
