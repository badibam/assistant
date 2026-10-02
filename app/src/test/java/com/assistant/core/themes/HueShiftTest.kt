package com.assistant.core.themes

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * A hue shift turns a theme's colours and leaves the states' as they are, in both modes and for
 * every theme: the interface settings offer every shift, and a colour lost on the way would show
 * only once chosen, on the phone.
 */
class HueShiftTest {

    private fun close(a: Color, b: Color) =
        abs(a.red - b.red) < 0.01f && abs(a.green - b.green) < 0.01f && abs(a.blue - b.blue) < 0.01f

    @Test
    fun `a whole turn, or none, gives the colours back`() {
        val blue = Color(0xFF4A68AE)
        assertEquals(blue, Oklch.rotate(blue, 0f))
        assertTrue(close(blue, Oklch.rotate(blue, 360f)))
        assertTrue(close(blue, Oklch.rotate(Oklch.rotate(blue, 120f), 240f)))
    }

    @Test
    fun `every theme turns its colours, not its states', in both modes`() {
        for ((id, theme) in ThemeScanner.scanForThemes()) for (mode in PaletteMode.entries) {
            val own = theme.getColorScheme(mode, 0)
            for (shift in listOf(90, 180, 270)) {
                val turned = theme.getColorScheme(mode, shift)
                // The border: always coloured, where the main colour can be a white, which has no hue
                assertNotEquals("$id ${mode.name} +$shift: the border turns", own.outline, turned.outline)
                assertEquals("$id ${mode.name} +$shift: the error stays", own.error, turned.error)
            }
        }
    }
}
