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

    /**
     * Items whose section changes from [before] to [after] (by id): each leaves its grid and
     * arrives in the other one, in the order of [tiles] on the screen (row, then column). [tiles]
     * holds the items of every section at their places. Returns the tiles that moved, at their
     * new place.
     */
    fun <K> regroup(tiles: List<Tile>, before: Map<String, K>, after: Map<String, K>): List<Tile> {
        val leaving = tiles.filter { before[it.id] != after[it.id] }.sortedWith(compareBy({ it.row }, { it.column }))
        if (leaving.isEmpty()) return emptyList()
        // Each section's grid as the sections stand now: the ones they had, then the ones they get
        val grids = tiles.groupBy { before[it.id] }.toMutableMap()
        for (tile in leaving) {
            val from = before[tile.id]
            val to = after[tile.id]
            grids[from] = leave(grids.getValue(from), tile.id)
            grids[to] = arrive(grids[to] ?: emptyList(), tile.id, Size(tile.width, tile.height))
        }
        val original = tiles.associateBy { it.id }
        return grids.values.flatten().filter { original[it.id] != it }
    }

    enum class Direction { LEFT, RIGHT, UP, DOWN }

    /**
     * Tile [id] moved by an arrow to the next place it fits in [direction], or null when there is
     * none (the arrow is greyed).
     *
     * Left and right keep its row and go a quarter at a time to the next column where it fits.
     * Up and down keep its column and stop at the first row where it fits or the first
     * interstice, whichever comes first: at an interstice, rows open for it and the ones after
     * move down. A row it leaves empty closes. A place that changes nothing is passed over.
     */
    fun move(tiles: List<Tile>, id: String, direction: Direction): List<Tile>? {
        val tile = tiles.single { it.id == id }
        val others = tiles.filter { it.id != id }
        val before = layout(closeEmptyRows(tiles))
        fun fits(column: Int, row: Int) = column >= 0 && column + tile.width <= COLUMNS && row >= 0 &&
            others.none { it.overlaps(tile.copy(column = column, row = row)) }
        fun changed(next: List<Tile>): List<Tile>? = closeEmptyRows(next).takeIf { layout(it) != before }

        return when (direction) {
            Direction.LEFT, Direction.RIGHT -> {
                val step = if (direction == Direction.LEFT) -1 else 1
                generateSequence(tile.column + step) { it + step }
                    .takeWhile { it >= 0 && it + tile.width <= COLUMNS }
                    .firstOrNull { fits(it, tile.row) }
                    ?.let { changed(others + tile.copy(column = it)) }
            }
            Direction.UP, Direction.DOWN -> {
                // The places in the column from top to bottom: interstice 0, row 0, interstice 1, row 1…
                // as 2 × boundary for an interstice and 2 × row + 1 for a row
                val last = 2 * rowCount(others)
                val here = 2 * tile.row + 1
                val order = if (direction == Direction.DOWN) (here + 1..last) else (here - 1 downTo 0)
                order.asSequence().mapNotNull { place ->
                    if (place % 2 == 1) {
                        val row = place / 2
                        if (fits(tile.column, row)) changed(others + tile.copy(row = row)) else null
                    } else {
                        val boundary = place / 2
                        if (isInterstice(others, boundary)) {
                            changed(others.map { if (it.row >= boundary) it.copy(row = it.row + tile.height) else it } + tile.copy(row = boundary))
                        } else null
                    }
                }.firstOrNull()
            }
        }
    }

    /**
     * True when [tiles] are a grid as the rules keep it: each inside the columns, none over
     * another, no row left empty.
     */
    fun isLaidOut(tiles: List<Tile>): Boolean =
        tiles.all { it.column >= 0 && it.row >= 0 && it.column + it.width <= COLUMNS } &&
            tiles.none { a -> tiles.any { b -> a !== b && a.overlaps(b) } } &&
            layout(closeEmptyRows(tiles)) == layout(tiles)

    /** The places of [tiles], whatever their order. */
    private fun layout(tiles: List<Tile>): Set<Tile> = tiles.toSet()

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
