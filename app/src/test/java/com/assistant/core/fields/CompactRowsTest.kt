package com.assistant.core.fields

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers how the compact layout of the user's fields lays them out: two short values side by
 * side, a long one alone on its row, in the fields' order.
 */
class CompactRowsTest {

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) =
        FieldDefinition(name = name, displayName = name, description = null, type = type, alwaysVisible = false, config = config)

    private val short = mapOf("length" to TextLength.SHORT.name)
    private val long = mapOf("length" to TextLength.LONG.name)

    private fun rowsOf(vararg fields: FieldDefinition) = compactRows(fields.toList()).map { row -> row.map { it.name } }

    @Test
    fun shortValuesGoTwoByTwo() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c")),
            rowsOf(field("a", FieldType.NUMERIC), field("b", FieldType.TEXT, short), field("c", FieldType.BOOLEAN))
        )
    }

    @Test
    fun aLongValueTakesItsOwnRow() {
        assertEquals(
            listOf(listOf("a"), listOf("note"), listOf("b", "c")),
            rowsOf(field("a", FieldType.NUMERIC), field("note", FieldType.TEXT, long), field("b", FieldType.DATE), field("c", FieldType.TIME))
        )
    }

    @Test
    fun aScaleAndARankingAreWide() {
        val scale = field("mood", FieldType.SCALE, mapOf("min" to 1, "max" to 5))
        val ranking = field("rank", FieldType.CHOICE, mapOf("options" to ChoiceSettings.storedOptions(listOf("x", "y")), "ordered" to true))
        assertEquals(listOf(listOf("mood"), listOf("rank")), rowsOf(scale, ranking))
    }
}
