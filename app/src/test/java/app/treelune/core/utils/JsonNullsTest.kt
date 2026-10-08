package app.treelune.core.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers what a writer takes out of an object written whole before checking and storing it: the
 * keys set to null, at every depth, objects inside a list included — and nothing of a list's own
 * elements, which would move the others. The validator checks what it is handed, so what it
 * checks is what is stored: a null it left out of its copy used to reach the database unchecked.
 */
class JsonNullsTest {

    @Test
    fun `a key set to null is taken out, in nested objects too`() {
        val cleared = JsonNulls.withoutNullKeys(JSONObject().put("a", JSONObject.NULL).put("b", 1)
            .put("o", JSONObject().put("c", JSONObject.NULL).put("d", "x")))
        assertFalse(cleared.has("a"))
        assertEquals(1, cleared.getInt("b"))
        assertFalse(cleared.getJSONObject("o").has("c"))
        assertEquals("x", cleared.getJSONObject("o").getString("d"))
    }

    @Test
    fun `a list keeps its elements, nulls and empty included, its objects cleared`() {
        val cleared = JsonNulls.withoutNullKeys(JSONObject()
            .put("bounds", JSONArray().put(JSONObject.NULL).put(5))
            .put("groups", JSONArray())
            .put("rows", JSONArray().put(JSONObject().put("k", JSONObject.NULL).put("v", 1))))
        val bounds = cleared.getJSONArray("bounds")
        assertEquals(2, bounds.length())
        assertTrue(bounds.isNull(0))
        assertEquals(0, cleared.getJSONArray("groups").length())
        assertFalse(cleared.getJSONArray("rows").getJSONObject(0).has("k"))
    }

    @Test
    fun `a map is cleared the same way`() {
        val cleared = JsonNulls.withoutNullKeys(mapOf("a" to null, "b" to mapOf("c" to null, "d" to 2), "l" to listOf(null, 3)))
        assertEquals(mapOf("b" to mapOf("d" to 2), "l" to listOf(null, 3)), cleared)
    }
}
