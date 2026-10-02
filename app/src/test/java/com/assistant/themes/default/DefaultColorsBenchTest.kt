package com.assistant.themes.default

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.assistant.core.themes.PaletteMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The default theme's bench page (bench/colors.html) draws the theme's colours, written out in its
 * script: they are the theme's own. A colour changed in DefaultTheme and not there would show on
 * the bench a look the app no longer has.
 */
class DefaultColorsBenchTest {

    private val page = File("src/main/java/com/assistant/themes/default/bench/colors.html").readText()

    /** The roles the page's LIGHT or DARK object names, with their "#rrggbb". */
    private fun pageColors(name: String): Map<String, String> {
        val body = Regex("""const $name = \{(.*?)\};""", RegexOption.DOT_MATCHES_ALL).find(page)?.groupValues?.get(1)
            ?: error("no $name in colors.html")
        return Regex("""(\w+): "(#[0-9A-Fa-f]{6})"""").findAll(body).associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    }

    private fun hex(scheme: ColorScheme, role: String): String {
        // A Color getter's name is mangled ("getPrimary-0d7_KjU"): the role, then a dash
        val name = "get${role.replaceFirstChar { c -> c.uppercase() }}"
        val getter = ColorScheme::class.java.methods.first { (it.name == name || it.name.startsWith("$name-")) && it.parameterCount == 0 }
        val packed = (getter.invoke(scheme) as Long).toULong()
        return "#%06X".format(androidx.compose.ui.graphics.Color(packed).toArgb() and 0xFFFFFF)
    }

    @Test
    fun `the page's colours are the theme's`() {
        for ((name, mode) in listOf("LIGHT" to PaletteMode.LIGHT, "DARK" to PaletteMode.DARK)) {
            val scheme = DefaultTheme.getColorScheme(mode, 0)
            for ((role, colour) in pageColors(name)) assertEquals("$name $role", hex(scheme, role), colour)
        }
    }

    @Test
    fun `the page's chart series are the drawing's`() {
        val series = Regex("""const SERIES = \{ LIGHT: \["(#\w{6})", "(#\w{6})"\], DARK: \["(#\w{6})", "(#\w{6})"\] \};""").find(page)
            ?.groupValues?.drop(1) ?: error("no SERIES in colors.html")
        fun hex(c: androidx.compose.ui.graphics.Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
        val tags = listOf(com.assistant.core.themes.TagColor.BLUE, com.assistant.core.themes.TagColor.ORANGE)
        val expected = listOf(false, true).flatMap { dark -> tags.map { hex(DefaultDrawing.color(it, dark)) } }
        assertEquals(expected, series.map { it.uppercase() })
    }
}
