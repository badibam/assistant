package app.treelune.core.fields

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the entry schema a tool type gets from its declaration: what a write must carry, what
 * it may not, and where each author's fields go. Every check runs real entries through the
 * validator the app uses.
 */
class EntrySchemaGeneratorTest {

    private val mapper = ObjectMapper()

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) =
        FieldDefinition(name, name, null, type, false, config)

    private fun schemaOf(declared: EntryFields, extra: List<FieldDefinition> = emptyList()): JsonSchema =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(EntrySchemaGenerator.generate(declared, extra) { it })

    private fun JsonSchema.accepts(entry: String): Boolean =
        validate(mapper.readTree(JSONObject(entry).put("tool_instance_id", "t").put("tooltype", "x").toString())).isEmpty()

    private val journal = EntryFields(
        data = listOf(FixedField(field("content", FieldType.TEXT), required = true))
    )

    @Test
    fun aRequiredName_mustBeGiven() {
        val schema = schemaOf(journal)

        assertTrue(schema.accepts("""{ "name": "Day", "data": { "content": "..." } }"""))
        assertFalse(schema.accepts("""{ "data": { "content": "..." } }"""))
        assertFalse(schema.accepts("""{ "name": "", "data": { "content": "..." } }"""))
    }

    /** An absent core field is refused rather than stored: a name nobody gives would be invented. */
    @Test
    fun anAbsentCoreField_isRefused() {
        val schema = schemaOf(EntryFields(name = CoreFieldUsage.ABSENT, timestamp = CoreFieldUsage.ABSENT))

        assertTrue(schema.accepts("""{ }"""))
        assertFalse(schema.accepts("""{ "name": "Invented" }"""))
        assertFalse(schema.accepts("""{ "timestamp": 1727000000000 }"""))
    }

    @Test
    fun fixedFields_areHeldToTheirTypeAndRequirement() {
        val schema = schemaOf(journal)

        assertFalse(schema.accepts("""{ "name": "Day", "data": { } }"""))
        assertFalse(schema.accepts("""{ "name": "Day", "data": { "content": 12 } }"""))
        assertFalse(schema.accepts("""{ "name": "Day", "data": { "content": "...", "mood": 3 } }"""))
    }

    /** A user's field lives in extra, never in data, so a fixed field added later cannot collide with it. */
    @Test
    fun userFields_liveInExtra() {
        val schema = schemaOf(journal, extra = listOf(field("mood", FieldType.NUMERIC, mapOf("decimals" to 0))))

        assertTrue(schema.accepts("""{ "name": "Day", "data": { "content": "..." }, "extra": { "mood": 3 } }"""))
        assertFalse(schema.accepts("""{ "name": "Day", "data": { "content": "...", "mood": 3 } }"""))
        assertFalse(schema.accepts("""{ "name": "Day", "data": { "content": "..." }, "extra": { "other": 1 } }"""))
    }

    @Test
    fun state_holdsTheDeclaredStateAndTheRunningDurations() {
        val declared = EntryFields(
            data = listOf(FixedField(field("value", FieldType.DURATION))),
            state = listOf(StateField(field("read", FieldType.BOOLEAN), filterable = true))
        )
        val schema = schemaOf(declared, extra = listOf(field("pause", FieldType.DURATION)))

        assertTrue(schema.accepts("""{ "name": "Run", "state": { "read": true } }"""))
        assertTrue(schema.accepts("""{ "name": "Run", "state": { "running": { "data": { "value": 1727000000000 }, "extra": { "pause": 1727000000000 } } } }"""))
        assertFalse(schema.accepts("""{ "name": "Run", "state": { "running": { "data": { "pause": 1727000000000 } } } }"""))
        assertFalse(schema.accepts("""{ "name": "Run", "state": { "archived": true } }"""))
    }

    /** Without a DURATION field there is nothing that can run. */
    @Test
    fun noDurationField_noRunningKey() {
        val schema = schemaOf(journal)

        assertFalse(schema.accepts("""{ "name": "Day", "data": { "content": "..." }, "state": { "running": { } } }"""))
    }

    /** What the app writes itself is marked, so the service drops it from what a caller sends. */
    @Test
    fun systemWrittenFields_areMarked() {
        val declared = EntryFields(data = listOf(FixedField(field("common_title", FieldType.TEXT), systemWritten = true)))
        val schema = JSONObject(EntrySchemaGenerator.generate(declared, emptyList()) { it })

        assertTrue(app.treelune.core.validation.SystemManagedFields.inData(schema.toString()).contains("common_title"))
    }
}
