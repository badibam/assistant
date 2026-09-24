package com.assistant.core.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the translation every command and every stored config goes through: Kotlin maps and
 * lists on one side, JSON on the other. What a service reads is what the caller built, nested
 * structures, nulls and numbers included; a value with no JSON form fails instead of arriving
 * as its text or as nothing.
 */
class JsonUtilsTest {

    private enum class Mode { DAILY }

    /** Maps inside lists inside maps come back as they left, through the stored text. */
    @Test
    fun nestedStructures_surviveTheRoundTrip() {
        val sent = mapOf(
            "name" to "Sleep",
            "custom_fields" to listOf(
                mapOf("name" to "quality", "config" to mapOf("min" to 1, "max" to 10)),
                mapOf("name" to "notes", "config" to mapOf("length" to "LONG"))
            ),
            "tags" to listOf("a", "b")
        )

        val stored = JsonUtils.toJSONObject(sent).toString()
        val read = JsonUtils.toMap(stored)

        assertEquals(sent, read)
    }

    /** The nested values are real JSON containers, which is what a service's optJSONArray needs. */
    @Test
    fun nestedValues_areJsonContainers() {
        val json = JsonUtils.toJSONObject(mapOf("list" to listOf(1), "map" to mapOf("k" to "v")))

        assertTrue(json.get("list") is JSONArray)
        assertTrue(json.get("map") is JSONObject)
    }

    /** A null is kept as JSON null and read back as null: how a command empties a field. */
    @Test
    fun aNull_isKeptAndReadBackAsNull() {
        val json = JsonUtils.toJSONObject(mapOf("description" to null, "inner" to mapOf("x" to null)))

        assertSame(JSONObject.NULL, json.get("description"))
        assertSame(JSONObject.NULL, json.getJSONObject("inner").get("x"))
        val read = JsonUtils.toMap(json.toString())
        assertTrue(read.containsKey("description"))
        assertNull(read["description"])
    }

    /** Whole numbers stay whole, and a decimal stays the same decimal. */
    @Test
    fun numbers_keepTheirKind() {
        val read = JsonUtils.toMap(JsonUtils.toJSONObject(mapOf(
            "small" to 7,
            "timestamp" to 1_727_000_000_000L,
            "decimal" to 3.5
        )).toString())

        assertEquals(7L, (read["small"] as Number).toLong())
        assertTrue(read["small"] is Int || read["small"] is Long)
        assertEquals(1_727_000_000_000L, read["timestamp"])
        assertEquals(3.5, read["decimal"])
    }

    /** A Set, an array and an enum value are the three Kotlin forms without a JSON twin that do get one. */
    @Test
    fun setsArraysAndEnums_getTheirJsonForm() {
        val json = JsonUtils.toJSONObject(mapOf(
            "set" to linkedSetOf("a", "b"),
            "array" to arrayOf(1, 2),
            "mode" to Mode.DAILY
        ))

        assertEquals(listOf("a", "b"), JsonUtils.toList(json.getJSONArray("set")))
        assertEquals(2, json.getJSONArray("array").length())
        assertEquals("DAILY", json.getString("mode"))
    }

    /** Any other value has no JSON form: its text would be stored in its place, so it fails. */
    @Test(expected = IllegalArgumentException::class)
    fun aValueWithNoJsonForm_fails() {
        JsonUtils.toJSONObject(mapOf("when" to java.util.Date()))
    }

    /** What is not an object is refused by toMap, rather than read as an object holding nothing. */
    @Test(expected = IllegalArgumentException::class)
    fun toMap_refusesAnArray() {
        JsonUtils.toMap(JSONArray("[1, 2]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun toList_refusesAnObject() {
        JsonUtils.toList(JSONObject())
    }

    /** No value and a blank column both read as nothing. */
    @Test
    fun nullAndBlank_readAsEmpty() {
        assertEquals(emptyMap<String, Any?>(), JsonUtils.toMap(null))
        assertEquals(emptyList<Any?>(), JsonUtils.toList(null))
        assertEquals(emptyList<Any?>(), JsonUtils.toList("  "))
    }
}
