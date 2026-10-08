package app.treelune.themes.cosy

import androidx.compose.ui.graphics.luminance
import app.treelune.core.themes.Lch
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.TagColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cosy palettes are read, one per mode, and every text they draw is legible where it is drawn:
 * a palette retouched by eye can otherwise lose a contrast nobody notices until it is on the phone.
 */
class CosyPaletteTest {

    private fun contrast(a: Lch, b: Lch): Float {
        val (light, dark) = listOf(a.srgb.luminance(), b.srgb.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun legible(name: String, ink: Lch, ground: Lch, least: Float = 4.5f) {
        val ratio = contrast(ink, ground)
        assertTrue("$name: $ratio, at least $least", ratio >= least)
    }

    @Test
    fun `one palette per mode`() {
        assertEquals(PaletteMode.entries.toSet(), CosyPalettes.entries.map { it.mode }.toSet())
        assertEquals(PaletteMode.entries.size, CosyPalettes.entries.size)
    }

    @Test
    fun `every text is legible on what it is drawn on`() {
        for (mode in PaletteMode.entries) {
            val c = CosyPalettes.colors(mode, 0)
            legible("$mode ink on a tile", c.ink, c.tile)
            legible("$mode ink on the ground", c.ink, c.ground)
            legible("$mode main colour's ink", c.onAccent, c.accent)
            TagColor.entries.forEach { legible("$mode tag $it", c.tagInk(it), c.tag(it)) }
            // The dim ink and the states are secondary, their texts short: the large text's ratio
            legible("$mode dim ink on a tile", c.dim, c.tile, 3f)
            legible("$mode dim ink on the ground", c.dim, c.ground, 3f)
            listOf(c.success, c.warning, c.error, c.info).forEach { legible("$mode state $it on a tile", it, c.tile, 3f) }
        }
    }

    @Test
    fun `a hue shift turns the palette and keeps the tags' hues`() {
        val base = CosyPalettes.colors(PaletteMode.LIGHT, 0)
        val turned = CosyPalettes.colors(PaletteMode.LIGHT, 120)
        assertTrue(base.ground.srgb != turned.ground.srgb)
        // The grey keeps a trace of the palette's hue, and turns with it
        TagColor.entries.filter { it != TagColor.GREY }.forEach { assertEquals(base.tag(it).srgb, turned.tag(it).srgb) }
        assertEquals(base.error.srgb, turned.error.srgb)
    }
}
