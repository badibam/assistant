package app.treelune.core.grid

import app.treelune.core.ui.DisplayMode

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
     * move down, only as many as it lacks when it can stand across the opened rows, its other
     * part above them going down and below them going up, on free cells. A row it leaves empty
     * closes. A place that changes nothing is passed over, and so
     * is one that does not take the tile further in [direction] from the other tiles: lower than
     * at least one of them for down and higher than none, the other way round for up. Without it,
     * rows opened under the top of a tall tile push the others down while the tile stays: down
     * would move them, not it.
     */
    fun move(tiles: List<Tile>, id: String, direction: Direction): List<Tile>? {
        val tile = tiles.single { it.id == id }
        val others = tiles.filter { it.id != id }
        val before = layout(closeEmptyRows(tiles))
        fun fits(column: Int, row: Int) = column >= 0 && column + tile.width <= COLUMNS && row >= 0 &&
            others.none { it.overlaps(tile.copy(column = column, row = row)) }
        fun changed(next: List<Tile>): List<Tile>? = closeEmptyRows(next).takeIf { layout(it) != before }
        /** Whether [next] has the tile further in [direction] from the others, as the arrow says. */
        fun goes(next: List<Tile>): Boolean {
            val sign = if (direction == Direction.DOWN) 1 else -1
            val after = next.associateBy { it.id }
            val moved = after.getValue(id).row
            val shifts = others.map { other -> sign * ((moved - after.getValue(other.id).row) - (tile.row - other.row)) }
            return shifts.any { it > 0 } && shifts.none { it < 0 }
        }

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
                        if (fits(tile.column, row)) changed(others + tile.copy(row = row))?.takeIf { goes(it) } else null
                    } else {
                        val boundary = place / 2
                        if (isInterstice(others, boundary)) {
                            // [opened] rows at the boundary, the fewest that make room: going down
                            // the tile ends at their bottom, going up it starts at their top
                            (1..tile.height).asSequence().mapNotNull { opened ->
                                val row = if (direction == Direction.DOWN) boundary + opened - tile.height else boundary
                                val shifted = others.map { if (it.row >= boundary) it.copy(row = it.row + opened) else it }
                                val placed = tile.copy(row = row)
                                if (row < 0 || shifted.any { it.overlaps(placed) }) null
                                else changed(shifted + placed)?.takeIf { goes(it) }
                            }.firstOrNull()
                        } else null
                    }
                }.firstOrNull()
            }
        }
    }

    /** [tiles] in the order a screen reads them: by row, then by column. */
    fun readingOrder(tiles: List<Tile>): List<Tile> = tiles.sortedWith(compareBy({ it.row }, { it.column }))

    /**
     * [order] laid on the grid in that order, at their sizes: each tile at the first free place at
     * or after the one before it, along its row then on the next ones, from the left. Read again
     * ([readingOrder]), the tiles come back in [order]. Whatever stood before (gaps, two tiles set
     * side by side) is not kept.
     */
    fun pack(order: List<Tile>): List<Tile> {
        val placed = mutableListOf<Tile>()
        var row = 0
        var column = 0
        for (tile in order) {
            while (true) {
                if (column + tile.width > COLUMNS) { row++; column = 0; continue }
                val here = tile.copy(column = column, row = row)
                if (placed.none { it.overlaps(here) }) { placed += here; break }
                column++
            }
        }
        return placed
    }

    /**
     * The size a tile of [width] × [height] takes in one column (the « Une colonne » setting): a
     * half of the grid becomes its whole width, a quarter a half; a tile of the whole width,
     * whose first row holds two halves side by side (its header and its summary), stacks them,
     * one row taller.
     */
    fun oneColumnSize(width: Int, height: Int): Size =
        if (width >= COLUMNS) Size(COLUMNS, height + 1) else Size(width * 2, height)

    /**
     * [tiles] as one column shows them: in their reading order, each at its size in one column
     * ([oneColumnSize]), packed: a tile on each row, two of a quarter side by side. A tile of
     * [grows] (FULL) keeps its single row, whose height is its content's. Returned in the order
     * of [tiles]. Nothing of it is stored: it is read from the grid each time.
     */
    fun oneColumn(tiles: List<Tile>, grows: Set<String> = emptySet()): List<Tile> {
        val shown = pack(readingOrder(tiles).map { tile ->
            val size = if (tile.id in grows) Size(tile.width, tile.height) else oneColumnSize(tile.width, tile.height)
            tile.copy(width = size.width, height = size.height)
        }).associateBy { it.id }
        return tiles.map { shown.getValue(it.id) }
    }

    /**
     * Tile [id] moved by an arrow in one column, or null when that way changes nothing. The
     * column's rows are read in order: up puts the tile before the row above, down after the row
     * below; left and right swap it with its neighbour on its row (two tiles of a quarter). The
     * grid is then packed in that new order ([pack]): what one column shows is what is stored,
     * and the grid's own layout of the section is not kept.
     */
    fun moveInOneColumn(tiles: List<Tile>, id: String, direction: Direction): List<Tile>? {
        val order = readingOrder(tiles).map { it.id }
        val shownRow = oneColumn(tiles).associate { it.id to it.row }
        // The column's rows, each the ids it starts, in order
        val rows = order.groupBy { shownRow.getValue(it) }.values.toList()
        val k = rows.indexOfFirst { id in it }
        val onRow = rows[k]
        val next: List<String> = when (direction) {
            Direction.LEFT, Direction.RIGHT -> {
                val at = onRow.indexOf(id)
                val other = onRow.getOrNull(if (direction == Direction.LEFT) at - 1 else at + 1) ?: return null
                order.map { when (it) { id -> other; other -> id; else -> it } }
            }
            Direction.UP -> {
                if (k == 0) return null
                val rest = order - id
                rest.toMutableList().apply { add(rest.indexOf(rows[k - 1].first()), id) }
            }
            Direction.DOWN -> {
                if (k == rows.lastIndex) return null
                val rest = order - id
                rest.toMutableList().apply { add(rest.indexOf(rows[k + 1].last()) + 1, id) }
            }
        }
        val byId = tiles.associateBy { it.id }
        val packed = pack(next.map { byId.getValue(it) })
        return packed.takeIf { layout(it) != layout(tiles) }
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
