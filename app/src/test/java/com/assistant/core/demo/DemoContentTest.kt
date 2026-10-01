package com.assistant.core.demo

import com.assistant.core.grid.Grid
import com.assistant.core.grid.ZonePositions
import com.assistant.core.ui.DisplayMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers the demo the app ships (docs/design/demo.md), read from its assets as the service reads
 * them: the same text keys in both languages, every key the structure asks for given, every id
 * carrying the prefix a reinstall removes by, and the zones laid out in their group with the
 * modes a zone's tile takes.
 */
class DemoContentTest {

    private fun asset(name: String) = JSONObject(File("src/main/assets/demo/$name").readText(Charsets.UTF_8))

    private val structure = asset("structure.json")
    private val languages = listOf("texts-en.json", "texts-fr.json")

    @Test
    fun `both languages give the same text keys`() {
        val (en, fr) = languages.map { asset(it).keys().asSequence().toSet() }
        assertEquals(en, fr)
    }

    @Test
    fun `the demo reads whole in each language, every id prefixed, every zone in its group`() {
        for (language in languages) {
            val content = DemoContent.read(structure, asset(language), now = 0)
            assertTrue(content.zones.isNotEmpty())
            content.zones.forEach { zone ->
                assertTrue(zone.id.startsWith(DemoContent.PREFIX))
                assertEquals(content.group, zone.group)
                // Each group named, none left as its key
                JSONArray(zone.tool_groups).let { groups -> (0 until groups.length()).forEach { assertTrue(groups.getString(it).isNotBlank()) } }
            }
        }
    }

    @Test
    fun `a text missing refuses the demo`() {
        val texts = asset("texts-en.json").apply { remove("course_name") }
        val refused = runCatching { DemoContent.read(structure, texts, now = 0) }
        assertTrue(refused.isFailure)
    }

    @Test
    fun `the zones take a zone's modes, all four of them, and lie on the grid without overlapping`() {
        val zones = DemoContent.read(structure, asset("texts-en.json"), now = 0).zones
        val modes = zones.map { DisplayMode.valueOf(it.display_mode) }
        assertTrue(ZonePositions.MODES.containsAll(modes))
        assertEquals(ZonePositions.MODES.toSet(), modes.toSet())
        assertTrue(Grid.isLaidOut(zones.map { ZonePositions.tile(it) }))
    }
}
