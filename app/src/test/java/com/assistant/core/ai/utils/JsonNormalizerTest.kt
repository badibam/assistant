package com.assistant.core.ai.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the step that turns what the model sent into something Kotlin can read.
 *
 * A reply arrives parsed by org.json, whose containers do not answer to Map or List: a cast
 * returns null rather than failing, so a command whose parameters were not normalised loses
 * them silently. This is the first of the three stages a command goes through, and the one
 * where the data is still shaped exactly as the model wrote it.
 *
 * The class says of itself that it "preserves all data, only changes types"; these cases
 * check that, nulls included.
 */
class JsonNormalizerTest {

    // ==================== Changing the containers ====================

    /** A JSON object becomes a Map, at any depth. */
    @Test
    fun aJsonObjectBecomesAMap() {
        val params = mapOf<String, Any>(
            "data" to JSONObject("""{"name": "Sport", "nested": {"deep": "value"}}""")
        )

        val result = JsonNormalizer.normalizeParams(params)

        val data = result["data"] as Map<*, *>
        assertEquals("Sport", data["name"])
        assertEquals("value", (data["nested"] as Map<*, *>)["deep"])
    }

    /** A JSON array becomes a List, and objects inside it become Maps. */
    @Test
    fun aJsonArrayBecomesAList() {
        val params = mapOf<String, Any>(
            "entries" to JSONArray("""[{"value": 1}, {"value": 2}]""")
        )

        val result = JsonNormalizer.normalizeParams(params)

        val entries = result["entries"] as List<*>
        assertEquals(2, entries.size)
        assertEquals(1, (entries[0] as Map<*, *>)["value"])
    }

    /** The awkward shape the AI actually sends: a list of objects under a nested key. */
    @Test
    fun aBatchOfEntriesComesThroughWhole() {
        val params = mapOf<String, Any>(
            "tool_instance_id" to "abc",
            "entries" to JSONArray(
                """[{"name": "Sport", "value": {"amount": 30, "unit": "min"}},
                    {"name": "Lecture", "value": {"amount": 45, "unit": "min"}}]"""
            )
        )

        val result = JsonNormalizer.normalizeParams(params)

        assertEquals("abc", result["tool_instance_id"])
        val entries = result["entries"] as List<*>
        assertEquals(2, entries.size)
        val second = entries[1] as Map<*, *>
        assertEquals("Lecture", second["name"])
        assertEquals(45, (second["value"] as Map<*, *>)["amount"])
    }

    /** Values already in Kotlin form are left alone, and nested ones are still walked. */
    @Test
    fun kotlinValuesAreLeftAsTheyAre() {
        val params = mapOf<String, Any>(
            "name" to "Sport",
            "count" to 3,
            "done" to true,
            "nested" to mapOf("inner" to listOf(1, 2))
        )

        val result = JsonNormalizer.normalizeParams(params)

        assertEquals("Sport", result["name"])
        assertEquals(3, result["count"])
        assertEquals(true, result["done"])
        assertEquals(listOf(1, 2), (result["nested"] as Map<*, *>)["inner"])
    }

    /** Numbers that are neither Int nor Long land on Double or Long by their written form. */
    @Test
    fun otherNumbersLandOnDoubleOrLong() {
        val params = mapOf<String, Any>(
            "weight" to JSONObject("""{"kilos": 72.5, "steps": 10000}""")
        )

        val result = JsonNormalizer.normalizeParams(params)
        val weight = result["weight"] as Map<*, *>

        assertEquals(72.5, weight["kilos"])
        assertEquals(10000, weight["steps"])
    }

    // ==================== Nulls ====================

    /**
     * A null inside an object survives, which is how a command asks for a field to be
     * emptied rather than left as it was.
     */
    @Test
    fun aNullInsideAnObjectSurvives() {
        val params = mapOf<String, Any>(
            "extra" to JSONObject("""{"mood": null, "notes": "kept"}""")
        )

        val result = JsonNormalizer.normalizeParams(params)
        val fields = result["extra"] as Map<*, *>

        assertTrue("the key is still there", fields.containsKey("mood"))
        assertNull(fields["mood"])
        assertEquals("kept", fields["notes"])
    }

    /**
     * A null at the top level is kept too. It used to lose its key, so a command emptying a
     * top-level field -- a zone's description -- arrived as one that never mentioned it.
     */
    @Test
    fun aNullAtTheTopLevel_keepsItsKey() {
        val params = mapOf<String, Any>(
            "description" to JSONObject.NULL,
            "zone_id" to "abc"
        )

        val result = JsonNormalizer.normalizeParams(params)

        assertTrue("the key is still there", result.containsKey("description"))
        assertNull(result["description"])
        assertEquals("abc", result["zone_id"])
    }

    /**
     * And it reaches the service as JSON null, which is what a service tests for to empty a
     * field: the coordinator hands the params over through JsonUtils.toJSONObject.
     */
    @Test
    fun aTopLevelNull_reachesTheServiceAsJsonNull() {
        val normalized = JsonNormalizer.normalizeParams(mapOf<String, Any>("description" to JSONObject.NULL))

        val handedOver = com.assistant.core.utils.JsonUtils.toJSONObject(normalized)

        assertTrue(handedOver.has("description"))
        assertTrue(handedOver.isNull("description"))
    }

    /**
     * A null inside a list is kept, whichever form the list arrived in. Dropping it
     * shortened the list and moved everything after the hole down a place, so the same data
     * came out differently depending only on whether it was still JSON.
     */
    @Test
    fun aNullInsideAList_isKeptWhicheverFormItArrivedIn() {
        val fromJson = JsonNormalizer.normalizeParams(
            mapOf<String, Any>("values" to JSONArray("""[1, null, 2]"""))
        )
        val fromKotlin = JsonNormalizer.normalizeParams(
            mapOf<String, Any>("values" to listOf(1, null, 2))
        )

        assertEquals(listOf(1, null, 2), fromJson["values"])
        assertEquals(listOf(1, null, 2), fromKotlin["values"])
    }

    /** Positions are what a null in a list protects: the values after it do not move. */
    @Test
    fun aNullInAListDoesNotShiftWhatFollowsIt() {
        val result = JsonNormalizer.normalizeParams(
            mapOf<String, Any>("values" to JSONArray("""["a", null, "b"]"""))
        )

        val values = result["values"] as List<*>
        assertEquals(3, values.size)
        assertEquals("b", values[2])
    }

    // ==================== Nothing to do ====================

    /** No parameters, nothing to normalise. */
    @Test
    fun emptyParamsStayEmpty() {
        assertEquals(emptyMap<String, Any>(), JsonNormalizer.normalizeParams(emptyMap()))
    }

    /** An empty object and an empty array keep their shape rather than vanishing. */
    @Test
    fun emptyContainersKeepTheirShape() {
        val result = JsonNormalizer.normalizeParams(
            mapOf<String, Any>("obj" to JSONObject("{}"), "arr" to JSONArray("[]"))
        )

        assertEquals(emptyMap<String, Any>(), result["obj"])
        assertEquals(emptyList<Any>(), result["arr"])
    }
}
