package com.assistant.core.fields

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers how the compact layout of the user's fields lays them out: two short values side by
 * side, a long one alone on its row, in the fields' order. Long is the value's, not the field's:
 * a text field is unlimited unless set otherwise, and most hold a few words.
 */
class CompactRowsTest {

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) =
        FieldDefinition(name = name, displayName = name, description = null, type = type, alwaysVisible = false, config = config)

    private val unlimited = mapOf("length" to TextLength.UNLIMITED.name)

    private fun rowsOf(values: Map<String, Any?>, vararg fields: FieldDefinition) =
        compactRows(fields.toList(), values).map { row -> row.map { it.name } }

    @Test
    fun shortValuesGoTwoByTwoWhateverTheTextLengthDeclared() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c")),
            rowsOf(mapOf("a" to 2, "b" to "Frais", "c" to true),
                field("a", FieldType.NUMERIC), field("b", FieldType.TEXT, unlimited), field("c", FieldType.BOOLEAN))
        )
    }

    @Test
    fun aLongTextTakesItsOwnRow() {
        val long = "A text that goes on well beyond a few words"
        assertEquals(
            listOf(listOf("a"), listOf("note"), listOf("b", "c")),
            rowsOf(mapOf("a" to 1, "note" to long, "b" to "2026-09-27", "c" to "08:00"),
                field("a", FieldType.NUMERIC), field("note", FieldType.TEXT, unlimited), field("b", FieldType.DATE), field("c", FieldType.TIME))
        )
    }

    @Test
    fun aTextOnSeveralLinesTakesItsOwnRow() {
        assertEquals(listOf(listOf("a"), listOf("b")), rowsOf(mapOf("a" to "one\ntwo", "b" to "x"), field("a", FieldType.TEXT), field("b", FieldType.TEXT)))
    }

    @Test
    fun aScaleAndARankingAreWide() {
        val scale = field("mood", FieldType.SCALE, mapOf("min" to 1, "max" to 5))
        val ranking = field("rank", FieldType.CHOICE, mapOf("options" to ChoiceSettings.storedOptions(listOf("x", "y")), "ordered" to true))
        assertEquals(listOf(listOf("mood"), listOf("rank")), rowsOf(mapOf("mood" to 3, "rank" to listOf("x", "y")), scale, ranking))
    }
}
