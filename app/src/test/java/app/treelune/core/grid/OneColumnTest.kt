package app.treelune.core.grid

import app.treelune.core.grid.Grid.Direction
import app.treelune.core.grid.Grid.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The « Une colonne » setting: what one column shows of a grid, and the grid an arrow leaves
 * there. On the phone these show only as tiles out of order or over each other after a move.
 */
class OneColumnTest {

    private fun icon(id: String, column: Int, row: Int) = Tile(id, column, row, 1, 1)
    private fun half(id: String, column: Int, row: Int) = Tile(id, column, row, 2, 1)
    private fun condensed(id: String, column: Int, row: Int) = Tile(id, column, row, 2, 2)
    private fun line(id: String, row: Int) = Tile(id, 0, row, 4, 1)

    private fun places(tiles: List<Tile>) = tiles.associate { it.id to (it.column to it.row) }
    private fun order(tiles: List<Tile>) = Grid.readingOrder(tiles).map { it.id }

    @Test
    fun aHalfTakesTheWholeWidth_aQuarterAHalf_aWholeWidthOneRowMore() {
        assertEquals(Grid.Size(4, 1), Grid.oneColumnSize(2, 1))
        assertEquals(Grid.Size(4, 2), Grid.oneColumnSize(2, 2))
        assertEquals(Grid.Size(2, 1), Grid.oneColumnSize(1, 1))
        assertEquals(Grid.Size(4, 2), Grid.oneColumnSize(4, 1))
        assertEquals(Grid.Size(4, 3), Grid.oneColumnSize(4, 2))
        assertEquals(Grid.Size(4, 5), Grid.oneColumnSize(4, 4))
    }

    @Test
    fun oneColumn_showsTheGridInItsReadingOrder_twoQuartersARow() {
        // a b on the first row, a line, then four icons
        val tiles = listOf(half("b", 2, 0), half("a", 0, 0), line("l", 1),
            icon("i1", 0, 2), icon("i2", 1, 2), icon("i3", 2, 2), icon("i4", 3, 2))
        val shown = Grid.oneColumn(tiles)
        assertEquals(tiles.map { it.id }, shown.map { it.id })
        assertEquals(
            mapOf("a" to (0 to 0), "b" to (0 to 1), "l" to (0 to 2), "i1" to (0 to 4), "i2" to (2 to 4), "i3" to (0 to 5), "i4" to (2 to 5)),
            places(shown)
        )
        assertTrue(Grid.isLaidOut(shown))
    }

    @Test
    fun pack_keepsTheOrder_andLeavesNoTileOverAnother() {
        val order = listOf(condensed("c", 3, 7), half("a", 0, 0), half("b", 0, 9), line("l", 2), icon("i", 1, 1))
        val packed = Grid.pack(order)
        assertEquals(order.map { it.id }, order(packed))
        assertEquals(mapOf("c" to (0 to 0), "a" to (2 to 0), "b" to (2 to 1), "l" to (0 to 2), "i" to (0 to 3)), places(packed))
        assertTrue(Grid.isLaidOut(packed))
    }

    @Test
    fun upAndDown_moveATileAcrossTheRowNextToIt() {
        val tiles = listOf(line("l", 0), half("a", 0, 1), half("b", 2, 1))
        val up = Grid.moveInOneColumn(tiles, "b", Direction.UP)!!
        assertEquals(listOf("l", "b", "a"), order(up))
        val top = Grid.moveInOneColumn(up, "b", Direction.UP)!!
        assertEquals(listOf("b", "l", "a"), order(top))
        assertEquals(listOf("b", "l", "a"), order(Grid.oneColumn(top)))
        val down = Grid.moveInOneColumn(top, "b", Direction.DOWN)!!
        assertEquals(listOf("l", "b", "a"), order(down))
        assertTrue(Grid.isLaidOut(down))
    }

    @Test
    fun leftAndRight_swapTwoQuartersOnTheirRow_onlyThem() {
        val tiles = listOf(icon("i1", 0, 0), icon("i2", 1, 0), line("l", 1))
        assertEquals(listOf("i2", "i1", "l"), order(Grid.moveInOneColumn(tiles, "i1", Direction.RIGHT)!!))
        assertNull(Grid.moveInOneColumn(tiles, "i1", Direction.LEFT))
        assertNull(Grid.moveInOneColumn(tiles, "l", Direction.LEFT))
        assertNull(Grid.moveInOneColumn(tiles, "l", Direction.RIGHT))
    }

    @Test
    fun anArrowOutOfTheColumn_isGreyed() {
        val tiles = listOf(line("l", 0), half("a", 0, 1))
        assertNull(Grid.moveInOneColumn(tiles, "l", Direction.UP))
        assertNull(Grid.moveInOneColumn(tiles, "a", Direction.DOWN))
    }

    @Test
    fun aMove_whatOneColumnShowsIsWhatIsStored() {
        val tiles = listOf(condensed("c", 0, 0), half("a", 2, 0), half("b", 2, 1), icon("i1", 0, 2), icon("i2", 3, 2), line("l", 3))
        for (id in tiles.map { it.id }) for (direction in Direction.entries) {
            val moved = Grid.moveInOneColumn(tiles, id, direction) ?: continue
            assertTrue("$id $direction laid out", Grid.isLaidOut(moved))
            assertFalse("$id $direction changed something", moved.toSet() == tiles.toSet())
            assertEquals(order(moved), order(Grid.oneColumn(moved)))
        }
    }

    @Test
    fun aTileThatGrows_keepsItsSingleRow() {
        val tiles = listOf(line("full", 0), half("a", 0, 1))
        assertEquals(mapOf("full" to (0 to 0), "a" to (0 to 1)), places(Grid.oneColumn(tiles, setOf("full"))))
    }
}
