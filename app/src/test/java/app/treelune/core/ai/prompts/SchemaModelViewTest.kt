package app.treelune.core.ai.prompts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.time.ZoneId

/**
 * Covers the view of a schema handed to the model.
 *
 * The app stores an instant as milliseconds and speaks ISO 8601 to the model, so the two ends
 * have different forms on purpose and each schema describes its own end. This view is computed
 * from the `"format": "epoch-millis"` mark the stored schema carries, which is what keeps the
 * two from being written twice.
 *
 * Nothing here needs a Context: the view is a function of the schema alone.
 */
class SchemaModelViewTest {

    private fun view(json: String): JSONObject =
        SchemaModelView.forModel(JSONObject(json), ZoneId.of("Europe/Paris"))

    /** A marked property is shown as the ISO string the model actually receives. */
    @Test
    fun aMarkedPropertyBecomesAnIsoString() {
        val result = view("""
            {"properties": {"timestamp": {"type": "number", "minimum": 0, "format": "epoch-millis"}}}
        """.trimIndent())

        val timestamp = result.getJSONObject("properties").getJSONObject("timestamp")
        assertEquals("string", timestamp.getString("type"))
        assertEquals("date-time", timestamp.getString("format"))
    }

    /** The numeric bounds go with the number: they would mean nothing against a string. */
    @Test
    fun theNumericBoundsAreDropped() {
        val result = view("""
            {"properties": {"t": {"type": "number", "minimum": 0, "maximum": 99, "format": "epoch-millis"}}}
        """.trimIndent())

        val t = result.getJSONObject("properties").getJSONObject("t")
        assertFalse("minimum", t.has("minimum"))
        assertFalse("maximum", t.has("maximum"))
    }

    /** Everything else the property said survives: the model still reads it. */
    @Test
    fun theDescriptionAndTheSystemFlagSurvive() {
        val result = view("""
            {"properties": {"created_at": {
                "type": "integer", "minimum": 0, "format": "epoch-millis",
                "system_managed": true, "description": "when the entry was written"
            }}}
        """.trimIndent())

        val createdAt = result.getJSONObject("properties").getJSONObject("created_at")
        assertEquals("when the entry was written", createdAt.getString("description"))
        assertTrue(createdAt.getBoolean("system_managed"))
    }

    /** A number that is not an instant carries no mark, and is left exactly as it was. */
    @Test
    fun anOrdinaryNumberIsLeftAlone() {
        val result = view("""
            {"properties": {"weight": {"type": "number", "minimum": 0, "maximum": 300}}}
        """.trimIndent())

        val weight = result.getJSONObject("properties").getJSONObject("weight")
        assertEquals("number", weight.getString("type"))
        assertEquals(0, weight.getInt("minimum"))
        assertEquals(300, weight.getInt("maximum"))
    }

    /**
     * A DATETIME custom field sits under custom_fields, two levels down, so the whole document is
     * walked rather than a known list of places.
     */
    @Test
    fun aMarkedPropertyIsReachedAtAnyDepth() {
        val result = view("""
            {"properties": {"extra": {"type": "object", "properties": {
                "bedtime": {"type": "number", "minimum": 0, "format": "epoch-millis"}
            }}}}
        """.trimIndent())

        val bedtime = result
            .getJSONObject("properties")
            .getJSONObject("extra")
            .getJSONObject("properties")
            .getJSONObject("bedtime")
        assertEquals("string", bedtime.getString("type"))
        assertEquals("date-time", bedtime.getString("format"))
    }

    /** Schemas hold arrays too, and a marked property inside one is reached the same way. */
    @Test
    fun aMarkedPropertyInsideAnArrayIsReached() {
        val result = view("""
            {"anyOf": [{"type": "number", "format": "epoch-millis"}, {"type": "null"}]}
        """.trimIndent())

        assertEquals("string", result.getJSONArray("anyOf").getJSONObject(0).getString("type"))
        assertEquals("null", result.getJSONArray("anyOf").getJSONObject(1).getString("type"))
    }

    /** A duration is shown as the ISO 8601 duration string the model reads and writes. */
    @Test
    fun aMarkedDurationBecomesAnIsoDurationString() {
        val result = view("""
            {"properties": {"sleep": {"type": "integer", "minimum": 0, "format": "duration-millis", "description": "d"}}}
        """.trimIndent())

        val sleep = result.getJSONObject("properties").getJSONObject("sleep")
        assertEquals("string", sleep.getString("type"))
        assertEquals("duration", sleep.getString("format"))
        assertFalse("minimum", sleep.has("minimum"))
        assertEquals("d", sleep.getString("description"))
    }

    /** A schema with no instant in it comes back unchanged. */
    @Test
    fun aSchemaWithoutInstantsIsUnchanged() {
        val source = """{"type": "object", "properties": {"name": {"type": "string"}}}"""

        assertEquals(
            JSONObject(source).toString(),
            SchemaModelView.forModel(JSONObject(source), ZoneId.of("Europe/Paris")).toString()
        )
    }
}
