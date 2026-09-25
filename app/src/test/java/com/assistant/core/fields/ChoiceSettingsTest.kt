package com.assistant.core.fields

import com.assistant.core.themes.TagColor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the settings of a CHOICE field as read from its config: its shape, its colors, and
 * what an open choice adds to its options.
 */
class ChoiceSettingsTest {

    private fun settings(config: String) = ChoiceSettings.fromConfig(JSONObject(config).toFieldConfig())

    @Test
    fun shape_comesFromTheFlags() {
        assertEquals(ChoiceShape.SINGLE, settings("""{ "options": ["a", "b"] }""").shape)
        assertEquals(ChoiceShape.MULTIPLE, settings("""{ "options": ["a", "b"], "multiple": true }""").shape)
        assertEquals(ChoiceShape.ORDERED, settings("""{ "options": ["a", "b"], "ordered": true }""").shape)
    }

    /** Colors are read from the stored JSON, which nests them as an object. */
    @Test
    fun colors_areReadByOption() {
        val read = settings("""{ "options": ["work", "home"], "option_colors": { "work": "BLUE" } }""")

        assertEquals(mapOf("work" to TagColor.BLUE), read.colors)
    }

    @Test
    fun anOpenChoice_addsWhatItDoesNotKnow_once() {
        val open = settings("""{ "options": ["work", "home"], "multiple": true, "open": true }""")

        assertEquals(listOf("sport"), open.newOptionsIn(listOf("work", "sport", "sport")))
        assertEquals(listOf("sport"), open.newOptionsIn("sport"))
        assertTrue(open.newOptionsIn(null).isEmpty())
    }

    /** A closed choice adds nothing: the schema refuses the unknown value instead. */
    @Test
    fun aClosedChoice_addsNothing() {
        val closed = settings("""{ "options": ["work", "home"] }""")

        assertTrue(closed.newOptionsIn("sport").isEmpty())
    }

    @Test
    fun withOptionsAdded_appendsToTheOptions() {
        val field = FieldDefinition("tags", "Tags", null, FieldType.CHOICE, false, mapOf("options" to listOf("work", "home"), "open" to true))

        val grown = field.withOptionsAdded(listOf("sport"))

        assertEquals(listOf("work", "home", "sport"), grown.config?.get("options"))
        assertEquals(true, grown.config?.get("open"))
    }
}
