package com.assistant.core.fields

import com.assistant.core.ui.SliderSteps
import com.assistant.core.validation.FieldLimits
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the rules a field's value is held to: FieldValueSchema turns each field type into a
 * JSON Schema, and every check below runs real values through the validator the app uses
 * (networknt, draft 7), so what is pinned is what an entry screen or the AI would be told.
 */
class FieldValueSchemaTest {

    private val mapper = ObjectMapper()

    /** The entry schema of a tool whose only field is the user's field [field] (its JSON, without name). */
    private fun schemaFor(field: String): JsonSchema {
        val definition = JSONObject(field).put("name", "f").put("display_name", "F").toFieldDefinition()
        val schema = EntrySchemaGenerator.generate(
            EntryFields(name = CoreFieldUsage.ABSENT, timestamp = CoreFieldUsage.ABSENT),
            listOf(definition)
        ) { it }
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schema)
    }

    /** Whether an entry holding [value] as the field passes. */
    private fun JsonSchema.accepts(value: String): Boolean =
        validate(mapper.readTree("""{ "tool_instance_id": "t", "tooltype": "x", "extra": { "f": $value } }""")).isEmpty()

    @Test
    fun text_isHeldToItsLength() {
        val short = schemaFor("""{ "type": "TEXT", "config": { "length": "SHORT" } }""")
        val unlimited = schemaFor("""{ "type": "TEXT" }""")
        val atLimit = "\"" + "a".repeat(FieldLimits.SHORT_LENGTH) + "\""
        val overLimit = "\"" + "a".repeat(FieldLimits.SHORT_LENGTH + 1) + "\""

        assertTrue(short.accepts(atLimit))
        assertFalse(short.accepts(overLimit))
        assertTrue(unlimited.accepts("\"" + "a".repeat(10_000) + "\""))
        assertFalse(unlimited.accepts("12"))
    }

    /** The decimals are checked exactly: 0.3 is one decimal, 0.35 is two. */
    @Test
    fun numeric_isHeldToItsBounds() {
        val schema = schemaFor("""{ "type": "NUMERIC", "config": { "min": 0, "max": 10, "decimals": 1 } }""")

        assertTrue(schema.accepts("0.3"))
        assertTrue(schema.accepts("10"))
        assertTrue("decimals are rounded at the write, not refused", schema.accepts("0.35"))
        assertFalse(schema.accepts("10.1"))
        assertFalse(schema.accepts("-1"))
    }

    /** A scale takes its stops only: 3.5 on a half-point scale, not 3.25, and nothing past max. */
    @Test
    fun scale_takesItsStopsOnly() {
        val halfPoints = schemaFor("""{ "type": "SCALE", "config": { "min": 0, "max": 5, "step": 0.5 } }""")
        val wholePoints = schemaFor("""{ "type": "SCALE", "config": { "min": 1, "max": 10 } }""")

        assertTrue(halfPoints.accepts("3.5"))
        assertFalse(halfPoints.accepts("3.25"))
        assertFalse(halfPoints.accepts("5.5"))
        assertTrue(wholePoints.accepts("1"))
        assertTrue(wholePoints.accepts("7"))
        assertFalse(wholePoints.accepts("7.5"))
        assertFalse(wholePoints.accepts("0"))
    }

    /**
     * The promise the scale rests on: for every config the validation accepts, each stop the
     * slider offers passes the value schema. multipleOf counts from 0, so this only holds
     * because the config keeps min and max on the step.
     */
    @Test
    fun everyStopOfAnAcceptedScale_passesItsSchema() {
        val scales = listOf(
            Triple(1.0, 10.0, 1.0),
            Triple(0.0, 5.0, 0.5),
            Triple(0.0, 1.0, 0.1),
            Triple(-5.0, 5.0, 1.0),
            Triple(4.0, 12.0, 0.5),
            Triple(0.0, 100.0, 10.0)
        )
        for ((min, max, step) in scales) {
            assertTrue(FieldConfigValidator.isMultipleOf(min, step) && FieldConfigValidator.isMultipleOf(max, step))
            val schema = schemaFor("""{ "type": "SCALE", "config": { "min": $min, "max": $max, "step": $step } }""")
            val stops = SliderSteps.innerStops(min, max, step) + 2
            for (i in 0 until stops) {
                val stop = SliderSteps.snap(min + i * step, min, step)
                assertTrue("$stop on $min..$max by $step", schema.accepts(stop.toString()))
            }
        }
    }

    /** The configs the validation refuses are the ones whose stops multipleOf would reject. */
    @Test
    fun boundsOffTheStep_areRefusedByTheConfigRule() {
        assertFalse(FieldConfigValidator.isMultipleOf(1.0, 2.0))
        assertFalse(FieldConfigValidator.isMultipleOf(0.5, 1.0))
        assertTrue(FieldConfigValidator.isMultipleOf(0.3, 0.1))
        assertTrue(FieldConfigValidator.isMultipleOf(-4.0, 2.0))
    }

    @Test
    fun choice_takesItsOptionsOnly() {
        val single = schemaFor("""{ "type": "CHOICE", "config": { "options": [{ "value": "low" }, { "value": "high" }] } }""")
        val multiple = schemaFor("""{ "type": "CHOICE", "config": { "options": [{ "value": "a" }, { "value": "b" }, { "value": "c" }], "multiple": true } }""")

        assertTrue(single.accepts("\"low\""))
        assertFalse(single.accepts("\"medium\""))
        assertTrue(multiple.accepts("""["a", "c"]"""))
        assertFalse(multiple.accepts("""["a", "d"]"""))
        assertFalse(multiple.accepts("""["a", "a"]"""))
    }

    @Test
    fun boolean_takesABooleanOnly() {
        val schema = schemaFor("""{ "type": "BOOLEAN" }""")

        assertTrue(schema.accepts("true"))
        assertFalse(schema.accepts("\"true\""))
    }

    /** Both ends are required and bounded; start <= end is not a schema rule and passes here. */
    @Test
    fun range_needsBothEndsWithinBounds() {
        val schema = schemaFor("""{ "type": "RANGE", "config": { "min": 0, "max": 10 } }""")

        assertTrue(schema.accepts("""{ "start": 2, "end": 8 }"""))
        assertFalse(schema.accepts("""{ "start": 2 }"""))
        assertFalse(schema.accepts("""{ "start": 2, "end": 11 }"""))
        assertTrue(schema.accepts("""{ "start": 8, "end": 3 }"""))
    }

    @Test
    fun dateAndTime_areStringsOfTheirShape() {
        val date = schemaFor("""{ "type": "DATE" }""")
        val time = schemaFor("""{ "type": "TIME" }""")

        assertTrue(date.accepts("\"2026-09-24\""))
        assertFalse(date.accepts("\"2026-13-45\""))
        assertFalse(date.accepts("\"24/09/2026\""))
        assertTrue(time.accepts("\"07:05\""))
        assertTrue(time.accepts("\"23:59\""))
        assertFalse(time.accepts("\"24:00\""))
    }

    /** An instant is stored in milliseconds, and says so for the view the AI reads. */
    @Test
    fun dateTime_isAnInstantInMilliseconds() {
        val schema = schemaFor("""{ "type": "DATETIME" }""")

        assertTrue(schema.accepts("1727000000000"))
        assertFalse(schema.accepts("\"2026-09-24T10:00:00\""))
        assertFalse(schema.accepts("-1"))
    }

    /** A ranking is a list of distinct options, and need not hold them all. */
    @Test
    fun orderedChoice_isAListOfDistinctOptions() {
        val schema = schemaFor("""{ "type": "CHOICE", "config": { "options": [{ "value": "a" }, { "value": "b" }, { "value": "c" }], "ordered": true } }""")

        assertTrue(schema.accepts("""["c", "a", "b"]"""))
        assertTrue(schema.accepts("""["b", "a"]"""))
        assertFalse(schema.accepts("""["a", "a"]"""))
        assertFalse(schema.accepts("""["a", "z"]"""))
        assertFalse(schema.accepts("\"a\""))
    }

    /** A duration is whole milliseconds, whatever precision it is entered in. */
    @Test
    fun duration_isWholeMilliseconds() {
        val schema = schemaFor("""{ "type": "DURATION", "config": { "precision": "HOUR" } }""")

        assertTrue(schema.accepts("5100000"))
        assertTrue(schema.accepts("1"))
        assertFalse(schema.accepts("1.5"))
        assertFalse(schema.accepts("-1"))
        assertFalse(schema.accepts("\"PT1H25M\""))
    }

    /** A value under a name the config does not declare is refused. */
    @Test
    fun anUndeclaredField_isRefused() {
        val schema = schemaFor("""{ "type": "BOOLEAN" }""")

        assertFalse(schema.validate(mapper.readTree("""{ "tool_instance_id": "t", "tooltype": "x", "extra": { "other": true } }""")).isEmpty())
    }

    /** A field the app cannot read fails instead of being left out silently. */
    @Test(expected = ValidationException::class)
    fun anUnreadableField_fails() {
        JSONObject("""{ "name": "f", "display_name": "F", "type": "COLOR" }""").toFieldDefinition()
    }
}
