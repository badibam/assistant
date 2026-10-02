package com.assistant.core.themes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every palette family of every theme exists in both modes: the interface settings let any family
 * be shown in any mode, and a missing one would fail only once chosen, on the phone.
 */
class ThemePalettesTest {

    @Test
    fun `every family of every theme is in both modes, once`() {
        for ((id, theme) in ThemeScanner.scanForThemes()) {
            val families = theme.paletteFamilies()
            assertTrue("$id: at least one family", families.isNotEmpty())
            for (family in families) for (mode in PaletteMode.entries) {
                val matching = theme.palettes().filter { it.family == family && it.mode == mode }
                assertEquals("$id: $family in ${mode.name}", 1, matching.size)
            }
            val ids = theme.palettes().map { it.id }
            assertEquals("$id: unique palette ids", ids.toSet().size, ids.size)
        }
    }
}
