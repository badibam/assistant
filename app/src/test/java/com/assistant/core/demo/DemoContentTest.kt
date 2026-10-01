package com.assistant.core.demo

import com.assistant.core.coordinator.GivenId
import com.assistant.core.coordinator.Source
import com.assistant.core.fields.FieldNameGenerator
import com.assistant.core.grid.Grid
import com.assistant.core.grid.ZonePositions
import com.assistant.core.ui.DisplayMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers the demo the app ships (docs/design/demo.md), read from its assets as the service reads
 * them: the same text keys in both languages, every key the structure asks for given, the prefix on every id and the suffix on every variable, ids unique, the zones and
 * each section of tools laid out on their grid, the user's field names kept as given, and the
 * given ids accepted from the app alone.
 */
class DemoContentTest {

    private fun asset(name: String) = JSONObject(File("src/main/assets/demo/$name").readText(Charsets.UTF_8))

    private val structure = asset("structure.json")
    private val languages = listOf("texts-en.json", "texts-fr.json")

    private fun read(language: String = "texts-en.json") = DemoContent.read(structure, asset(language))

    @Test
    fun `both languages give the same text keys`() {
        val (en, fr) = languages.map { asset(it).keys().asSequence().toSet() }
        assertEquals(en, fr)
    }

    @Test
    fun `the demo reads whole in each language, every id prefixed and unique, every variable suffixed`() {
        for (language in languages) {
            val content = read(language)
            val ids = (content.zones + content.tools + content.variables).map { it.getString("id") }
            assertEquals(ids.size, ids.toSet().size)
            content.zones.forEach { assertEquals(content.group, it.getString("group")) }
        }
    }

    @Test
    fun `a text missing refuses the demo`() {
        val texts = asset("texts-en.json").apply { remove("course_name") }
        assertTrue(runCatching { DemoContent.read(structure, texts) }.isFailure)
    }

    @Test
    fun `the zones take a zone's modes, all four of them, and lie on the grid without overlapping`() {
        val zones = read().zones
        val modes = zones.map { DisplayMode.valueOf(it.getString("display_mode")) }
        assertEquals(ZonePositions.MODES.toSet(), modes.toSet())
        assertTrue(Grid.isLaidOut(zones.map { tile(it, it.getString("display_mode")) }))
    }

    @Test
    fun `each section of tools lies on its grid, in a group its zone has`() {
        val content = read()
        val groups = content.zones.associate { it.getString("id") to it.getJSONArray("tool_groups").strings() }
        content.tools.groupBy { it.getString("zone_id") to it.getJSONObject("config").optString("group") }.forEach { (section, tools) ->
            if (section.second.isNotEmpty()) assertTrue(section.toString(), section.second in groups.getValue(section.first))
            assertTrue(section.toString(), Grid.isLaidOut(tools.map { tile(it, it.getJSONObject("config").getString("display_mode")) }))
        }
    }

    @Test
    fun `the user's fields are named as given, the generator keeping each name`() {
        read().tools.forEach { tool ->
            val fields = tool.getJSONObject("config").optJSONArray("extra_fields") ?: return@forEach
            (0 until fields.length()).map { fields.getJSONObject(it).getString("name") }.forEach { name ->
                assertEquals(name, FieldNameGenerator.generateName(name, emptyList()))
            }
        }
    }

    @Test
    fun `every id a config names exists, and a tool another one reads is created before it`() {
        val content = read()
        val tools = content.tools.map { it.getString("id") }
        val variables = content.variables.map { it.getString("id") }
        val created = mutableSetOf<String>()
        // As the service creates them: the tools reading no variable, the variables, the others
        val (readingVariables, others) = content.tools.partition { DemoContent.readsVariable(it) }
        val order = others.map { it.getString("id") to it.getJSONObject("config") } +
            content.variables.map { it.getString("id") to it.getJSONObject("definition") } +
            readingVariables.map { it.getString("id") to it.getJSONObject("config") }
        for ((id, body) in order) {
            Regex("\"(demo-[a-z0-9-]+)\"").findAll(body.toString()).map { it.groupValues[1] }.forEach { named ->
                assertTrue("$id names $named, which the demo does not create", named in tools || named in variables)
                assertTrue("$id names $named, not created before it", named in created)
            }
            created.add(id)
        }
    }

    @Test
    fun `the demo shows every tool type, tracking type and tile mode, and every field type among the user's fields`() {
        val content = read()
        assertEquals(setOf("tracking", "goal", "chart", "journal", "list", "messages", "notes", "questionnaire", "structured"),
            content.tools.map { it.getString("tooltype") }.toSet())
        assertEquals(setOf("numeric", "counter", "scale", "choice", "boolean", "text", "timer", "occurrence"),
            content.tools.filter { it.getString("tooltype") == "tracking" }.map { it.getJSONObject("config").getString("type") }.toSet())
        assertEquals(DisplayMode.entries.map { it.name }.toSet(),
            content.tools.map { it.getJSONObject("config").getString("display_mode") }.toSet())
        val fieldTypes = content.tools.flatMap { tool ->
            tool.getJSONObject("config").optJSONArray("extra_fields")?.let { f -> (0 until f.length()).map { f.getJSONObject(it).getString("type") } } ?: emptyList()
        }.toSet()
        assertEquals(com.assistant.core.fields.FieldType.entries.map { it.name }.toSet(), fieldTypes)
    }

    @Test
    fun `the automations are off, each with its instruction, a catch-up with a schedule only, in a group its zone has`() {
        for (language in languages) {
            val content = read(language)
            assertEquals(3, content.automations.size)
            val groups = content.zones.associate { it.getString("id") to it.getJSONArray("tool_groups").strings() }
            content.automations.forEach { automation ->
                assertFalse(automation.getBoolean("is_enabled"))
                assertTrue(automation.getString("seed").isNotBlank())
                assertEquals(automation.has("schedule"), automation.has("catch_up"))
                automation.optString("group").takeIf { it.isNotEmpty() }?.let { assertTrue(it in groups.getValue(automation.getString("zone_id"))) }
            }
        }
    }

    @Test
    fun `a field whose type has no settings carries no config`() {
        read().tools.forEach { tool ->
            val fields = tool.getJSONObject("config").optJSONArray("extra_fields") ?: return@forEach
            (0 until fields.length()).map { fields.getJSONObject(it) }.forEach { field ->
                val type = com.assistant.core.fields.FieldType.valueOf(field.getString("type"))
                if (com.assistant.core.fields.settings.FieldTypeSettings.configNodes(type) { it }.isEmpty())
                    assertFalse("${tool.getString("id")} ${field.getString("name")}", field.has("config"))
            }
        }
    }

    @Test
    fun `names and descriptions keep within their lengths, in each language`() {
        val short = com.assistant.core.validation.FieldLimits.SHORT_LENGTH
        val medium = com.assistant.core.validation.FieldLimits.MEDIUM_LENGTH
        for (language in languages) {
            val content = read(language)
            fun check(what: String, text: String, limit: Int) = assertTrue("$language $what: ${text.length} > $limit", text.length <= limit)
            content.zones.forEach { check(it.getString("id"), it.getString("name"), short); check(it.getString("id"), it.getString("description"), medium) }
            content.tools.forEach { tool ->
                val config = tool.getJSONObject("config")
                check(tool.getString("id"), config.getString("name"), short)
                check(tool.getString("id"), config.getString("description"), medium)
                config.optJSONArray("extra_fields")?.let { f -> (0 until f.length()).forEach { check(tool.getString("id"), f.getJSONObject(it).getString("display_name"), short) } }
            }
            content.automations.forEach { check(it.getString("id"), it.getString("name"), short) }
        }
    }

    @Test
    fun `an id is given by the app alone, with the demo's prefix`() {
        assertTrue(GivenId.isAccepted("demo-course", Source.SYSTEM))
        assertFalse(GivenId.isAccepted("demo-course", Source.AI))
        assertFalse(GivenId.isAccepted("demo-course", Source.USER))
        assertFalse(GivenId.isAccepted("course", Source.SYSTEM))
    }

    private fun tile(item: JSONObject, mode: String): Grid.Tile {
        val size = Grid.size(DisplayMode.valueOf(mode))
        return Grid.Tile(item.getString("id"), item.getInt("grid_x"), item.getInt("grid_y"), size.width, size.height)
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
