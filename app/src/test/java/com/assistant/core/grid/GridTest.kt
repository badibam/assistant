package com.assistant.core.grid

import com.assistant.core.grid.Grid.Size
import com.assistant.core.grid.Grid.Tile
import com.assistant.core.ui.DisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The placement rules of the grid: where a tile arrives, what its leaving closes, and what a tile
 * that grows or shrinks does to the others.
 * On the phone these show only as tiles overlapping or rows left empty, found by chance.
 */
class GridTest {

    private val icon = Grid.size(DisplayMode.ICON)
    private val minimal = Grid.size(DisplayMode.MINIMAL)
    private val line = Grid.size(DisplayMode.LINE)
    private val condensed = Grid.size(DisplayMode.CONDENSED)

    /** Each tile's column and row, by id. */
    private fun places(tiles: List<Tile>) = tiles.associate { it.id to (it.column to it.row) }

    private fun assertLaidOut(tiles: List<Tile>) {
        for (tile in tiles) {
            assertTrue("$tile inside the columns", tile.column >= 0 && tile.column + tile.width <= Grid.COLUMNS)
            for (other in tiles) if (other !== tile) assertFalse("$tile over $other", tile.overlaps(other))
        }
        for (row in 0 until Grid.rowCount(tiles)) {
            assertTrue("row $row taken", tiles.any { row >= it.row && row < it.bottom })
        }
    }

    @Test
    fun eachMode_takesTheCellsOfTheSpec() {
        val sizes = DisplayMode.values().associateWith { Grid.size(it) }
        assertEquals(
            mapOf(
                DisplayMode.ICON to Size(1, 1), DisplayMode.MINIMAL to Size(2, 1), DisplayMode.LINE to Size(4, 1),
                DisplayMode.CONDENSED to Size(2, 2), DisplayMode.EXTENDED to Size(4, 2), DisplayMode.SQUARE to Size(4, 4),
                DisplayMode.FULL to Size(4, 1)
            ),
            sizes
        )
    }

    @Test
    fun anArrivingTile_goesUnderAll_onARowOfItsOwn_inColumnZero() {
        val tiles = listOf(Tile("a", 0, 0, 2, 2), Tile("b", 2, 0, 1, 1))
        val arrived = Grid.arrive(tiles, "c", icon)
        assertEquals(0 to 2, places(arrived)["c"])
        assertEquals(0 to 0, places(Grid.arrive(emptyList(), "c", line))["c"])
    }

    @Test
    fun aLeavingTile_leavesAHole_andARowLeftEmptyCloses() {
        val tiles = listOf(Tile("a", 0, 0, 2, 1), Tile("b", 2, 0, 2, 1), Tile("c", 0, 1, 4, 1), Tile("d", 0, 2, 1, 1))
        // b leaves a hole beside a
        assertEquals(mapOf("a" to (0 to 0), "c" to (0 to 1), "d" to (0 to 2)), places(Grid.leave(tiles, "b")))
        // c leaves its row empty: d moves up into it
        assertEquals(mapOf("a" to (0 to 0), "b" to (2 to 0), "d" to (0 to 1)), places(Grid.leave(tiles, "c")))
    }

    /** The spec's drawing: a goes from MINIMAL to CONDENSED and covers c, which moves down. */
    @Test
    fun aGrowingTile_movesWhatItCovers_intoRowsOpenedUnderIt() {
        val before = listOf(
            Tile("a", 0, 0, 2, 1), Tile("b", 2, 0, 2, 1),
            Tile("c", 0, 1, 1, 1), Tile("d", 2, 1, 2, 1),
            Tile("e", 0, 2, 2, 1)
        )
        val after = Grid.resize(before, "a", condensed)
        assertEquals(
            mapOf("a" to (0 to 0), "b" to (2 to 0), "d" to (2 to 1), "c" to (0 to 2), "e" to (0 to 3)),
            places(after)
        )
        assertLaidOut(after)
    }

    @Test
    fun coveredTiles_keepTheirLayoutAmongThemselves() {
        // a becomes LINE: b and c, side by side on its row, move down together
        val before = listOf(Tile("a", 0, 0, 1, 1), Tile("b", 1, 0, 1, 1), Tile("c", 3, 0, 1, 1), Tile("d", 0, 1, 4, 1))
        val after = Grid.resize(before, "a", line)
        assertEquals(mapOf("a" to (0 to 0), "b" to (1 to 1), "c" to (3 to 1), "d" to (0 to 2)), places(after))
        assertLaidOut(after)
    }

    @Test
    fun rowsOpen_atTheFirstInterstice_neverAcrossATallTile() {
        // a grows to 2×2 over c; d, two rows high beside, crosses the limit under a
        val before = listOf(
            Tile("a", 0, 0, 2, 1), Tile("b", 2, 0, 2, 1),
            Tile("c", 0, 1, 1, 1), Tile("d", 2, 1, 2, 2),
            Tile("e", 0, 3, 4, 1)
        )
        val after = Grid.resize(before, "a", condensed)
        assertEquals(mapOf("a" to (0 to 0), "b" to (2 to 0), "d" to (2 to 1), "c" to (0 to 3), "e" to (0 to 4)), places(after))
        assertLaidOut(after)
    }

    @Test
    fun aTileCrossingTheRightEdge_comesBackLeft() {
        val before = listOf(Tile("a", 3, 0, 1, 1), Tile("b", 0, 0, 1, 1))
        val after = Grid.resize(before, "a", minimal)
        assertEquals(mapOf("a" to (2 to 0), "b" to (0 to 0)), places(after))
        // Full width, it covers all its row
        val full = Grid.resize(before, "a", line)
        assertEquals(mapOf("a" to (0 to 0), "b" to (0 to 1)), places(full))
    }

    @Test
    fun aShrinkingTile_staysAndLeavesAHole_itsEmptiedRowsClosing() {
        val before = listOf(Tile("a", 0, 0, 4, 4), Tile("b", 0, 4, 1, 1))
        val after = Grid.resize(before, "a", icon)
        assertEquals(mapOf("a" to (0 to 0), "b" to (0 to 1)), places(after))
        val condensedToMinimal = Grid.resize(listOf(Tile("a", 0, 0, 2, 2), Tile("b", 2, 0, 2, 1)), "a", minimal)
        assertEquals(mapOf("a" to (0 to 0), "b" to (2 to 0)), places(condensedToMinimal))
    }

    @Test
    fun anInterstice_isALimitNoTileCrosses() {
        val tiles = listOf(Tile("a", 0, 0, 2, 2), Tile("b", 2, 0, 2, 1))
        assertTrue(Grid.isInterstice(tiles, 0))
        assertFalse(Grid.isInterstice(tiles, 1))
        assertTrue(Grid.isInterstice(tiles, 2))
    }

    // Arrows (« Le mode d'édition »)

    private fun move(tiles: List<Tile>, id: String, direction: Grid.Direction) = Grid.move(tiles, id, direction)?.let { places(it) }

    @Test
    fun leftAndRight_goToTheNextColumnWhereTheTileFits_elseGreyed() {
        val tiles = listOf(Tile("a", 0, 0, 1, 1), Tile("b", 2, 0, 1, 1))
        assertEquals(mapOf("a" to (1 to 0), "b" to (2 to 0)), move(tiles, "a", Grid.Direction.RIGHT))
        assertEquals(mapOf("a" to (3 to 0), "b" to (2 to 0)), move(listOf(Tile("a", 1, 0, 1, 1), Tile("b", 2, 0, 1, 1)), "a", Grid.Direction.RIGHT))
        assertEquals(null, move(listOf(Tile("a", 3, 0, 1, 1)), "a", Grid.Direction.RIGHT))
        assertEquals(null, move(listOf(Tile("a", 0, 0, 2, 1), Tile("b", 2, 0, 1, 1)), "a", Grid.Direction.RIGHT))
        assertEquals(null, move(tiles, "a", Grid.Direction.LEFT))
    }

    @Test
    fun down_opensARowAtTheFirstInterstice_thenGoesUnderTheNext() {
        val tiles = listOf(Tile("a", 0, 0, 2, 1), Tile("b", 2, 0, 2, 1), Tile("c", 0, 1, 4, 1))
        val once = Grid.move(tiles, "a", Grid.Direction.DOWN)!!
        assertEquals(mapOf("a" to (0 to 1), "b" to (2 to 0), "c" to (0 to 2)), places(once))
        // The next interstice would leave things as they are: passed over, a goes under c
        assertEquals(mapOf("a" to (0 to 2), "b" to (2 to 0), "c" to (0 to 1)), move(once, "a", Grid.Direction.DOWN))
        assertLaidOut(Grid.move(once, "a", Grid.Direction.DOWN)!!)
    }

    @Test
    fun up_fillsAHoleInItsColumn_andItsEmptiedRowCloses() {
        val tiles = listOf(Tile("a", 0, 0, 1, 1), Tile("b", 1, 1, 1, 1))
        assertEquals(mapOf("a" to (0 to 0), "b" to (1 to 0)), move(tiles, "b", Grid.Direction.UP))
    }

    @Test
    fun up_overAFullRow_opensARowAboveIt() {
        val tiles = listOf(Tile("b", 0, 0, 4, 1), Tile("a", 0, 1, 1, 1))
        assertEquals(mapOf("a" to (0 to 0), "b" to (0 to 1)), move(tiles, "a", Grid.Direction.UP))
    }

    @Test
    fun vertical_neverOpensARowAcrossATallTile() {
        val tiles = listOf(Tile("a", 0, 0, 1, 1), Tile("d", 2, 0, 2, 2))
        assertEquals(mapOf("a" to (0 to 1), "d" to (2 to 0)), move(tiles, "a", Grid.Direction.DOWN))
    }

    @Test
    fun anArrowWithNowhereToGo_isGreyed() {
        assertEquals(null, move(listOf(Tile("a", 0, 0, 4, 1)), "a", Grid.Direction.DOWN))
        assertEquals(null, move(listOf(Tile("a", 0, 0, 4, 1)), "a", Grid.Direction.UP))
    }

    @Test
    fun aLaidOutGrid_hasItsTilesInside_noneOverAnother_noRowEmpty() {
        assertTrue(Grid.isLaidOut(listOf(Tile("a", 0, 0, 2, 2), Tile("b", 2, 0, 2, 1))))
        assertFalse(Grid.isLaidOut(listOf(Tile("a", 3, 0, 2, 1))))
        assertFalse(Grid.isLaidOut(listOf(Tile("a", 0, 0, 2, 1), Tile("b", 1, 0, 2, 1))))
        assertFalse(Grid.isLaidOut(listOf(Tile("a", 0, 0, 1, 1), Tile("b", 0, 2, 1, 1))))
    }
}
