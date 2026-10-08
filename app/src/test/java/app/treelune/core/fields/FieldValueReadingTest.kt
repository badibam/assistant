package app.treelune.core.fields

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers what an entry schema says besides the shape of a value: its label, and what the value
 * means. A 3, a 5 or a true says nothing on its own; the AI reads entries with their schema and
 * nothing else, so every setting that gives a value its meaning has to be written there.
 */
class FieldValueReadingTest {

    private val texts = mapOf(
        "field_reading_unit" to "In %1\$s.",
        "field_reading_scale" to "Scale from %1\$s to %2\$s.",
        "field_reading_bound" to "%1\$s (%2\$s)",
        "field_reading_boolean" to "true: %1\$s; false: %2\$s.",
        "field_reading_options" to "Options: %1\$s.",
        "field_reading_open" to "Others accepted.",
        "field_reading_whole" to "Whole.",
        "field_reading_decimals" to "%1\$d decimals.",
        "field_reading_ordered" to "A ranking."
    )
    private val text: (String) -> String = { texts[it] ?: it }

    private fun field(type: FieldType, config: Map<String, Any>?, description: String? = null) =
        FieldDefinition(name = "f", displayName = "Label", description = description, type = type, alwaysVisible = false, config = config)

    /** The schema of the field [f] as the generated entry schema holds it, among the user's fields. */
    private fun schemaOf(f: FieldDefinition): JSONObject =
        JSONObject(EntrySchemaGenerator.generate(EntryFields(), listOf(f), text))
            .getJSONObject("properties").getJSONObject("extra").getJSONObject("properties").getJSONObject("f")

    @Test
    fun theLabelIsTheTitle() {
        assertEquals("Label", schemaOf(field(FieldType.TEXT, null)).getString("title"))
    }

    @Test
    fun aNumberSaysItsUnit() {
        assertEquals("In km. 1 decimals.", schemaOf(field(FieldType.NUMERIC, mapOf("unit" to "km", "decimals" to 1))).getString("description"))
        assertEquals("Whole.", schemaOf(field(FieldType.NUMERIC, mapOf("decimals" to 0))).getString("description"))
    }

    @Test
    fun aScaleSaysWhatItsBoundsMean() {
        val scale = field(FieldType.SCALE, mapOf("min" to -5, "max" to 5.0, "min_label" to "Very bad", "max_label" to "Very good"))
        assertEquals("Scale from -5 (Very bad) to 5 (Very good).", schemaOf(scale).getString("description"))
    }

    @Test
    fun aBooleanSaysWhatItsAnswersMean() {
        val read = field(FieldType.BOOLEAN, mapOf("true_label" to "Read", "false_label" to "Unread"))
        assertEquals("true: Read; false: Unread.", schemaOf(read).getString("description"))
    }

    /** An open choice lists its options without closing them, and a ranking says it is one. */
    @Test
    fun aChoiceSaysItsOptionsAndWhetherItIsOpenOrARanking() {
        val open = field(FieldType.CHOICE, mapOf("options" to ChoiceSettings.storedOptions(listOf("work", "family")), "open" to true))
        assertEquals("Options: work, family. Others accepted.", schemaOf(open).getString("description"))

        val ranking = field(FieldType.CHOICE, mapOf("options" to ChoiceSettings.storedOptions(listOf("a", "b")), "ordered" to true))
        assertEquals("Options: a, b. A ranking.", schemaOf(ranking).getString("description"))
    }

    /** The user's own description follows what the settings say. */
    @Test
    fun theUsersDescriptionFollows() {
        val walk = field(FieldType.NUMERIC, mapOf("unit" to "km", "decimals" to 1), description = "Walked today")
        assertEquals("In km. 1 decimals. Walked today", schemaOf(walk).getString("description"))
    }

    /** A value whose settings add nothing gets no description at all rather than an empty one. */
    @Test
    fun nothingToSayIsNoDescription() {
        assertFalse(schemaOf(field(FieldType.TEXT, null)).has("description"))
    }
}
