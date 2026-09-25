package com.assistant.core.fields.settings

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers reading a config through its declaration: an absent setting means its declared
 * default, or nothing; the settings of the chosen variant are the ones read.
 */
class SettingValuesTest {

    private fun field(name: String, type: FieldType, default: Any? = null) =
        SettingNode.Field(FieldDefinition(name, name, null, type, false, null), default = default)

    private val declaration = listOf(
        field("group", FieldType.TEXT),
        SettingNode.Variant(
            selector = field("type", FieldType.CHOICE, default = "numeric"),
            cases = mapOf(
                "numeric" to emptyList(),
                "counter" to listOf(field("allow_decrement", FieldType.BOOLEAN, default = true))
            )
        )
    )

    @Test
    fun anAbsentSettingMeansItsDefault() {
        val counter = SettingValues(declaration, JSONObject("""{ "type": "counter" }"""))

        assertTrue(counter.boolean("allow_decrement"))
        assertFalse(SettingValues(declaration, JSONObject("""{ "type": "counter", "allow_decrement": false }""")).boolean("allow_decrement"))
    }

    @Test
    fun anAbsentSettingWithoutDefaultMeansNothing() {
        assertNull(SettingValues(declaration, JSONObject()).string("group"))
    }

    /** The variant's own settings are the chosen option's, its default option when none is stored. */
    @Test
    fun theChosenVariantsSettingsAreRead() {
        assertEquals("numeric", SettingValues(declaration, JSONObject()).string("type"))
        assertThrows("another option's setting", IllegalStateException::class.java) {
            SettingValues(declaration, JSONObject("""{ "type": "numeric" }""")).boolean("allow_decrement")
        }
    }

    /** A name the declaration does not hold is a mistake in the code, said loudly. */
    @Test
    fun anUndeclaredNameFails() {
        assertThrows(IllegalStateException::class.java) { SettingValues(declaration, JSONObject()).string("colour") }
    }
}
