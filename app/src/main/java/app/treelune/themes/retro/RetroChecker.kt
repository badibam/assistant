package app.treelune.themes.retro

import kotlin.math.roundToInt

/**
 * The checker a grid in edit mode is drawn on: a tile aside shows on one pixel in two, and the
 * cells under it (GridCell) show on the others, where their dotted outline is lit. Both count the
 * pixels from the screen's top left corner, so they agree wherever a cell and a tile stand: a cell
 * and a tile are whole pixels apart, but not always an even number of them.
 */
internal object RetroChecker {

    /** The pixel, counted from the screen's edge, that [x] screen pixels from it fall in at [scale]. */
    fun pixel(x: Float, scale: Int): Int = (x / scale).roundToInt()

    /** Whether [x], [y], pixels from the screen's corner, is open: a dot of a cell's outline, what is under a tile aside. */
    fun open(x: Int, y: Int): Boolean = (x + y) and 1 == 0

    /**
     * The pixels a tile whose corner is at [left], [top] on the screen moves the checker's
     * pattern by, across: none when its corner is open, one otherwise. The pattern, two by two
     * pixels, is open at its own top left and bottom right.
     */
    fun shift(left: Int, top: Int): Int = if (open(left, top)) 0 else 1

    /** Whether the pattern moved by [shift] is open at [x], [y], pixels in the tile: what is cut out of it. */
    fun hole(x: Int, y: Int, shift: Int): Boolean = open(x - shift, y)

    /**
     * The dots of a cell [width] by [height] pixels whose corner is at [left], [top] on the
     * screen: the pixels round its edge that are open, as places in the cell.
     */
    fun cellDots(width: Int, height: Int, left: Int, top: Int): List<Pair<Int, Int>> = buildList {
        for (y in 0 until height) for (x in 0 until width) {
            val edge = x == 0 || y == 0 || x == width - 1 || y == height - 1
            if (edge && open(left + x, top + y)) add(x to y)
        }
    }
}
