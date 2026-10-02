package com.assistant.themes.retro

import com.assistant.core.themes.PaletteMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The retro palettes derive in the theme the colours they derive on the bench.
 *
 * The bench (bench/palettes.html) draws with its own derivation and saves what it drew beside the
 * numbers; a theme deriving otherwise would show on the phone colours nobody chose, found only by
 * holding the phone next to the bench.
 */
class RetroPaletteTest {

    private fun hex(lch: Lch): String {
        val c = lch.srgb
        return "#%02x%02x%02x".format((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())
    }

    /** Two colours a step of 8 bits apart are the same: the bench computes in doubles, the theme in floats. */
    private fun close(a: String, b: String): Boolean =
        (1..5 step 2).all { abs(a.substring(it, it + 2).toInt(16) - b.substring(it, it + 2).toInt(16)) <= 1 }

    @Test
    fun `every palette derives the bench's colours`() {
        for (entry in RetroPalettes.entries) {
            val named = RetroColors(entry.numbers).named
            assertEquals("${entry.id}: the same names as the bench", entry.derived.keys, named.keys)
            for ((name, expected) in entry.derived) {
                val actual = hex(named.getValue(name))
                assertTrue("${entry.id} $name: bench $expected, theme $actual", close(expected, actual))
            }
        }
    }

    @Test
    fun `one palette per mode`() {
        assertEquals(PaletteMode.entries.toList(), RetroPalettes.entries.map { it.mode }.sorted())
    }
}
