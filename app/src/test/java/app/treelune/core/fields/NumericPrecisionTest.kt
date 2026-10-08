package app.treelune.core.fields

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Covers the precision of a NUMERIC value: a write is rounded to the field's decimals rather than
 * refused, so what is stored always holds the decimals the field says.
 */
class NumericPrecisionTest {

    private fun numeric(decimals: Int?) = FieldDefinition("f", "F", null, FieldType.NUMERIC, false,
        decimals?.let { mapOf("decimals" to it) })

    @Test
    fun aValueWithMoreDecimalsIsRoundedHalfUp() {
        assertEquals(72.5, NumericPrecision.round(72.456, numeric(1)))
        assertEquals(0.35, NumericPrecision.round(0.345, numeric(2)))
    }

    /** With no decimals, a whole number: a counter never stores 2.5. */
    @Test
    fun noDecimalsIsAWholeNumber() {
        assertEquals(3L, NumericPrecision.round(2.5, numeric(0)))
        assertEquals(7L, NumericPrecision.round(7, numeric(0)))
    }

    @Test
    fun onlyTheNumericFieldsOfTheEntryAreRounded() {
        val fields = listOf(numeric(0), FieldDefinition("t", "T", null, FieldType.TEXT, false, null))
        val rounded = JSONObject(NumericPrecision.roundAll("""{ "f": 4.6, "t": "4.6", "other": 1.25 }""", fields)!!)

        assertEquals(5L, rounded.getLong("f"))
        assertEquals("4.6", rounded.getString("t"))
        assertEquals(1.25, rounded.getDouble("other"), 0.0)
    }

    /** A range's two bounds are rounded alike. */
    @Test
    fun aRangesBoundsAreRounded() {
        val range = FieldDefinition("r", "R", null, FieldType.RANGE, false, mapOf("decimals" to 1))
        val rounded = JSONObject(NumericPrecision.roundAll("""{ "r": { "start": 1.26, "end": 3.04 } }""", listOf(range))!!).getJSONObject("r")

        assertEquals(1.3, rounded.getDouble("start"), 0.0)
        assertEquals(3.0, rounded.getDouble("end"), 0.0)
    }

    /** Every NUMERIC declares its decimals: one that does not is a declaration to fix, said loudly. */
    @Test
    fun aNumericWithoutDecimalsIsRefused() {
        assertThrows(IllegalStateException::class.java) { NumericPrecision.round(1.5, numeric(null)) }
    }
}
