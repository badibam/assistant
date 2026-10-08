package app.treelune.core.ai.validation

import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers what a validation request shows of an entry the AI proposes: the values it gives, in
 * the order of the tool's fields, and nothing it does not give.
 */
class ProposedEntriesTest {

    private val text: (String) -> String = { it }

    private fun field(name: String, type: FieldType = FieldType.TEXT) =
        FieldDefinition(name = name, displayName = name, description = null, type = type, alwaysVisible = false, config = null)

    private val declared = EntryFields(
        name = CoreFieldUsage.REQUIRED,
        timestamp = CoreFieldUsage.OPTIONAL,
        data = listOf(
            FixedField(field("value", FieldType.NUMERIC), required = true),
            FixedField(field("unit")),
            FixedField(field("sent_title"), systemWritten = true)
        )
    )
    private val extra = listOf(field("mood"), field("note"))

    @Test
    fun theValuesGivenInTheOrderOfTheFields() {
        val entry = mapOf(
            "extra" to mapOf("mood" to "calm"),
            "data" to mapOf("unit" to "kg", "value" to 75.5),
            "timestamp" to 1_700_000_000_000L,
            "name" to "Weighing"
        )
        val shown = ProposedEntries.valuesOf(entry, declared, extra, text).map { it.field.name to it.value }
        assertEquals(
            listOf("name" to "Weighing", "timestamp" to 1_700_000_000_000L, "value" to 75.5, "unit" to "kg", "mood" to "calm"),
            shown
        )
    }

    /** An update shows what it changes; a copy the app writes itself is not the AI's proposal. */
    @Test
    fun onlyWhatTheAiGives() {
        val entry = mapOf("data" to mapOf("value" to 76, "sent_title" to "x"))
        assertEquals(listOf("value"), ProposedEntries.valuesOf(entry, declared, extra, text).map { it.field.name })
    }

    @Test
    fun anAbsentCoreFieldIsNotShown() {
        val noName = declared.copy(name = CoreFieldUsage.ABSENT)
        assertEquals(emptyList<String>(), ProposedEntries.valuesOf(mapOf("name" to "x"), noName, extra, text).map { it.field.name })
    }
}
