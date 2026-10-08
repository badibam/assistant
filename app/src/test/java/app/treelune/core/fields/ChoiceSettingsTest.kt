package app.treelune.core.fields

import app.treelune.core.themes.TagColor
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
        assertEquals(ChoiceShape.SINGLE, settings("""{ "options": [{ "value": "a" }, { "value": "b" }] }""").shape)
        assertEquals(ChoiceShape.MULTIPLE, settings("""{ "options": [{ "value": "a" }, { "value": "b" }], "multiple": true }""").shape)
        assertEquals(ChoiceShape.ORDERED, settings("""{ "options": [{ "value": "a" }, { "value": "b" }], "ordered": true }""").shape)
    }

    /** Colors are read from the stored JSON, which nests them as an object. */
    @Test
    fun colors_areReadByOption() {
        val read = settings("""{ "options": [{ "value": "work", "color": "BLUE" }, { "value": "home" }] }""")

        assertEquals(mapOf("work" to TagColor.BLUE), read.colors)
    }

    @Test
    fun anOpenChoice_addsWhatItDoesNotKnow_once() {
        val open = settings("""{ "options": [{ "value": "work" }, { "value": "home" }], "multiple": true, "open": true }""")

        assertEquals(listOf("sport"), open.newOptionsIn(listOf("work", "sport", "sport")))
        assertEquals(listOf("sport"), open.newOptionsIn("sport"))
        assertTrue(open.newOptionsIn(null).isEmpty())
    }

    /** A closed choice adds nothing: the schema refuses the unknown value instead. */
    @Test
    fun aClosedChoice_addsNothing() {
        val closed = settings("""{ "options": [{ "value": "work" }, { "value": "home" }] }""")

        assertTrue(closed.newOptionsIn("sport").isEmpty())
    }

    @Test
    fun withOptionsAdded_appendsToTheOptions() {
        val field = FieldDefinition("tags", "Tags", null, FieldType.CHOICE, false, mapOf("options" to ChoiceSettings.storedOptions(listOf("work", "home")), "open" to true))

        val grown = field.withOptionsAdded(listOf("sport"))

        assertEquals(listOf("work", "home", "sport"), ChoiceSettings.fromConfig(grown.config).options)
        assertEquals(true, grown.config?.get("open"))
    }
}
