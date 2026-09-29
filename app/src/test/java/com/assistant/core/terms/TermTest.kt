package com.assistant.core.terms

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A constant keeps the value it was written with, whatever its type: a number as before, a text,
 * several options; the terms stored as bare numbers read as they did.
 */
class TermTest {

    private fun read(json: String) = Term.fromJson(JSONObject(json), "t") { it }

    @Test
    fun aBareNumber_readsAsBefore() {
        assertEquals(2100, (read("""{"constant": 2100}""") as Term.Constant).value)
    }

    @Test
    fun aConstantOfAnyType_writesAndReadsBack() {
        for (value in listOf<Any>(21_600_000, 2.5, "good", true, listOf("a", "b"))) {
            val written = Term.Constant(value).toJson().toString()
            assertEquals(value, (read(written) as Term.Constant).value)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun aConstantWithoutValue_isRefused() {
        read("""{"constant": null}""")
    }
}
