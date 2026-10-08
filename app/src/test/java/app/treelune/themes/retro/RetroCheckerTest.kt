package app.treelune.themes.retro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid in edit mode: a tile aside lets through every dot of the cells under it, wherever the
 * tile and the cells stand, an odd or an even number of pixels apart; and it keeps half its pixels.
 */
class RetroCheckerTest {

    @Test
    fun `every dot of a cell under a tile aside shows through it`() {
        // A tile at every corner parity, over cells odd and even distances from it, odd and even sized
        for (left in 7..8) for (top in 20..21) {
            val shift = RetroChecker.shift(left, top)
            for (dx in listOf(0, 11, 23, 46)) for (dy in listOf(0, 13, 24)) for ((w, h) in listOf(33 to 31, 34 to 32)) {
                RetroChecker.cellDots(w, h, left + dx, top + dy).forEach { (x, y) ->
                    assertTrue("cell at +$dx,+$dy, dot $x,$y, tile at $left,$top", RetroChecker.hole(dx + x, dy + y, shift))
                }
            }
        }
    }

    @Test
    fun `a tile aside keeps one pixel in two, and a cell's outline is dotted`() {
        val shift = RetroChecker.shift(5, 8)
        assertEquals(50, (0 until 10).sumOf { y -> (0 until 10).count { x -> !RetroChecker.hole(x, y, shift) } })
        // The top edge of a cell eleven pixels wide: every other pixel
        assertEquals(listOf(0, 2, 4, 6, 8, 10), RetroChecker.cellDots(11, 5, 4, 6).filter { it.second == 0 }.map { it.first })
    }
}
