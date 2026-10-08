package app.treelune.core.ai.prompts

import app.treelune.core.fields.CoreFieldUsage
import app.treelune.core.fields.EntryFields
import app.treelune.core.fields.EntrySchemaGenerator
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FixedField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONObject
import java.time.ZoneId

/**
 * Covers the values of an entry on their way to the model and back: an instant and a duration
 * are milliseconds inside the app and ISO 8601 facing the model, and the entry's schema -- not
 * a field's name, not the look of its value -- says where each one sits.
 *
 * The schema is the generated one, so what is checked is what the AI boundary actually meets.
 */
class ModelValuesTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun field(name: String, type: FieldType) =
        FieldDefinition(name = name, displayName = name, description = null, type = type, alwaysVisible = false, config = null)

    /** 2025-03-15T14:30:00+01:00 */
    private val instant = 1_742_045_400_000L

    /** 1 h 25 min */
    private val duration = 5_100_000L

    /**
     * A tool whose fixed field is a DURATION, with a user's DATETIME and TEXT beside it: the
     * three places a date or a duration can hide, and a text that looks like one.
     */
    private val schema: JSONObject = JSONObject(EntrySchemaGenerator.generate(
        EntryFields(
            name = CoreFieldUsage.OPTIONAL,
            timestamp = CoreFieldUsage.OPTIONAL,
            data = listOf(FixedField(field("value", FieldType.DURATION))),
            state = emptyList()
        ),
        listOf(
            field("appointment", FieldType.DATETIME),
            field("note", FieldType.TEXT)
        ),
        text = { it }
    ))

    private fun stored() = JSONObject()
        .put("timestamp", instant)
        .put("data", JSONObject().put("value", duration))
        .put("extra", JSONObject().put("appointment", instant).put("note", "2025-03-15T14:30:00"))
        .put("state", JSONObject().put("running", JSONObject().put("data", JSONObject().put("value", instant))))

    @Test
    fun theCoreTheFixedTheUserAndTheStateValuesAreWrittenInIso() {
        val out = ModelValues.toModel(stored(), schema, paris) as JSONObject

        assertEquals("2025-03-15T14:30:00+01:00", out.get("timestamp"))
        assertEquals("PT1H25M", out.getJSONObject("data").get("value"))
        assertEquals("2025-03-15T14:30:00+01:00", out.getJSONObject("extra").get("appointment"))
        assertEquals(
            "2025-03-15T14:30:00+01:00",
            out.getJSONObject("state").getJSONObject("running").getJSONObject("data").get("value")
        )
    }

    /** A text that looks like a date stays text, both ways: the schema says it is a text. */
    @Test
    fun aTextThatLooksLikeADateIsLeftAlone() {
        val out = ModelValues.toModel(stored(), schema, paris) as JSONObject
        val back = ModelValues.fromModel(out, schema, paris) as JSONObject

        assertEquals("2025-03-15T14:30:00", back.getJSONObject("extra").get("note"))
        val pt = JSONObject().put("extra", JSONObject().put("note", "PT1H"))
        assertEquals("PT1H", (ModelValues.fromModel(pt, schema, paris) as JSONObject).getJSONObject("extra").get("note"))
    }

    @Test
    fun whatGoesOutComesBackTheSame() {
        val back = ModelValues.fromModel(ModelValues.toModel(stored(), schema, paris), schema, paris) as JSONObject

        assertEquals(instant, back.get("timestamp"))
        assertEquals(duration, back.getJSONObject("data").get("value"))
        assertEquals(instant, back.getJSONObject("extra").get("appointment"))
    }

    /** The model's params arrive as maps and lists, and are read the same way. */
    @Test
    fun mapsAreReadLikeObjects() {
        val params = mapOf(
            "timestamp" to "2025-03-15T14:30:00",
            "data" to mapOf("value" to "P1DT2H")
        )

        @Suppress("UNCHECKED_CAST")
        val back = ModelValues.fromModel(params, schema, paris) as Map<String, Any?>

        assertEquals(instant, back["timestamp"])
        assertEquals(26 * 3_600_000L, (back["data"] as Map<*, *>)["value"])
    }

    /** A number where a date or a duration sits is already the stored form, and is kept. */
    @Test
    fun aNumberFromTheModelIsKept() {
        val back = ModelValues.fromModel(JSONObject().put("data", JSONObject().put("value", 60_000)), schema, paris) as JSONObject

        assertEquals(60_000, back.getJSONObject("data").get("value"))
    }

    /** A date or a duration that does not read is refused, naming it, rather than stored as text. */
    @Test
    fun anUnreadableValueIsRefused() {
        val badDuration = JSONObject().put("data", JSONObject().put("value", "1h25"))
        val badDate = JSONObject().put("timestamp", "tomorrow")

        val e = assertThrows(IllegalArgumentException::class.java) { ModelValues.fromModel(badDuration, schema, paris) }
        assertEquals(true, e.message!!.contains("1h25"))
        assertThrows(IllegalArgumentException::class.java) { ModelValues.fromModel(badDate, schema, paris) }
    }

    /** A key the schema does not describe is carried as it is: the validation decides. */
    @Test
    fun anUndescribedKeyIsCarried() {
        val out = ModelValues.fromModel(JSONObject().put("elsewhere", "2025-03-15T14:30:00"), schema, paris) as JSONObject

        assertEquals("2025-03-15T14:30:00", out.get("elsewhere"))
    }
}
