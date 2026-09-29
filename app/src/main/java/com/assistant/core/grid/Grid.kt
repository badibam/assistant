package com.assistant.core.grid

import com.assistant.core.ui.DisplayMode

/**
 * The grid a group lays its tiles on: four columns of square cells, rows numbered from 0 at the
 * top, as many as its tiles need. Pure placement, no storage: each function takes the tiles of one
 * grid and returns them all, moved where the rule puts them.
 *
 * A row is a row of cells. A tile's height counts in rows, except FULL's, which grows with what it
 * shows and is drawn as tall as that: it is alone on its row whatever its height, so placing it
 * takes one row.
 */
object Grid {

    const val COLUMNS = 4

    data class Size(val width: Int, val height: Int)

    /** A tile at [column] and [row], [width] × [height] cells. */
    data class Tile(val id: String, val column: Int, val row: Int, val width: Int, val height: Int) {
        val bottom: Int get() = row + height

        fun overlaps(other: Tile): Boolean =
            column < other.column + other.width && other.column < column + width &&
                row < other.bottom && other.row < bottom
    }

    /** The cells a tile takes in [mode]. */
    fun size(mode: DisplayMode): Size = when (mode) {
        DisplayMode.ICON -> Size(1, 1)
        DisplayMode.MINIMAL -> Size(2, 1)
        DisplayMode.LINE -> Size(4, 1)
        DisplayMode.CONDENSED -> Size(2, 2)
        DisplayMode.EXTENDED -> Size(4, 2)
        DisplayMode.SQUARE -> Size(4, 4)
        DisplayMode.FULL -> Size(4, 1)
    }

    /** The rows [tiles] take, the empty ones between them included. */
    fun rowCount(tiles: List<Tile>): Int = tiles.maxOfOrNull { it.bottom } ?: 0

    /** True when no tile crosses the limit above row [boundary]: a row can be opened there. */
    fun isInterstice(tiles: List<Tile>, boundary: Int): Boolean =
        tiles.none { it.row < boundary && boundary < it.bottom }

    /** A tile arriving in the grid: at the bottom, on a row of its own, in column 0. */
    fun arrive(tiles: List<Tile>, id: String, size: Size): List<Tile> =
        tiles + Tile(id, 0, rowCount(tiles), size.width, size.height)

    /** Tile [id] leaving the grid: its cells are left empty, a row left empty closes. */
    fun leave(tiles: List<Tile>, id: String): List<Tile> =
        closeEmptyRows(tiles.filter { it.id != id })

    /**
     * Tile [id] taking [size] where it is, its column brought left if it would cross the right
     * edge. A tile it grows over moves down, with the others it grows over, into rows opened at
     * the first interstice under it: they keep their columns and their layout among themselves,
     * and the rows below move down by as many. A tile that shrinks leaves its cells empty.
     */
    fun resize(tiles: List<Tile>, id: String, size: Size): List<Tile> {
        val tile = tiles.single { it.id == id }
        val resized = tile.copy(column = minOf(tile.column, COLUMNS - size.width), width = size.width, height = size.height)
        val others = tiles.filter { it.id != id }
        val covered = others.filter { it.overlaps(resized) }
        val staying = others - covered.toSet() + resized
        if (covered.isEmpty()) return closeEmptyRows(staying)

        // The covered tiles have left, so their cells no longer hold the interstice back
        val opening = (resized.bottom..rowCount(staying)).first { isInterstice(staying, it) }
        val top = covered.minOf { it.row }
        val height = covered.maxOf { it.bottom } - top
        val shifted = staying.map { if (it.row >= opening) it.copy(row = it.row + height) else it }
        val moved = covered.map { it.copy(row = opening + it.row - top) }
        return closeEmptyRows(shifted + moved)
    }

    /** [tiles] with each row that no tile takes removed, the rows below it moving up. */
    fun closeEmptyRows(tiles: List<Tile>): List<Tile> {
        val taken = BooleanArray(rowCount(tiles))
        tiles.forEach { tile -> for (row in tile.row until tile.bottom) taken[row] = true }
        // Each row's number once the empty rows above it are gone
        val closed = IntArray(taken.size)
        var empty = 0
        for (row in taken.indices) {
            closed[row] = row - empty
            if (!taken[row]) empty++
        }
        return tiles.map { it.copy(row = closed[it.row]) }
    }
}
