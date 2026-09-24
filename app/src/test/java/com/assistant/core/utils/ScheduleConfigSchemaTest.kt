package com.assistant.core.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the agreement between ScheduleConfig and the schema the AI reads when it writes the
 * recurrence of a Messages tool: what the app stores passes the schema, and what the schema
 * lets through reads back into the same schedule. A gap either way is a schedule that is
 * refused when it is fine, or accepted and then never run.
 */
class ScheduleConfigSchemaTest {

    private val mapper = ObjectMapper()

    /** Loaded the way Messages embeds it: without its bare "$id", which the validator cannot resolve alone. */
    private val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(
        (mapper.readTree(ScheduleConfigSchema.content { it }) as com.fasterxml.jackson.databind.node.ObjectNode)
            .apply { remove("\$id") }
    )

    private fun passes(json: String) = schema.validate(mapper.readTree(json)).isEmpty()

    private val onePerPattern = listOf(
        SchedulePattern.DailyMultiple(listOf("09:00", "18:30")),
        SchedulePattern.WeeklySimple(listOf(1, 3, 5), "07:15"),
        SchedulePattern.MonthlyRecurrent(listOf(1, 6), 15, "10:00"),
        SchedulePattern.WeeklyCustom(listOf(WeekMoment(1, "09:00"), WeekMoment(7, "20:00"))),
        SchedulePattern.YearlyRecurrent(listOf(YearlyDate(12, 25, "08:00"))),
        SchedulePattern.SpecificDates(listOf(1_727_000_000_000L))
    )

    /** Each pattern, as the app encodes and stores it, passes the schema. */
    @Test
    fun whatTheAppStores_passesTheSchema() {
        for (pattern in onePerPattern) {
            val stored = Json.encodeToString(ScheduleConfig.serializer(), ScheduleConfig(pattern, startDate = 1_727_000_000_000L))
            assertTrue(stored, passes(stored))
        }
    }

    /** Each pattern written by the schema's rules reads back, with the reader that refuses unknown keys. */
    @Test
    fun whatTheSchemaAccepts_readsBack() {
        val written = listOf(
            """{"pattern":{"type":"DailyMultiple","times":["09:00","18:30"]}}""",
            """{"pattern":{"type":"WeeklySimple","days_of_week":[1,3,5],"time":"07:15"}}""",
            """{"pattern":{"type":"MonthlyRecurrent","months":[1,6],"day_of_month":15,"time":"10:00"}}""",
            """{"pattern":{"type":"WeeklyCustom","moments":[{"day_of_week":1,"time":"09:00"},{"day_of_week":7,"time":"20:00"}]}}""",
            """{"pattern":{"type":"YearlyRecurrent","dates":[{"month":12,"day":25,"time":"08:00"}]}}""",
            """{"pattern":{"type":"SpecificDates","timestamps":[1727000000000]},"start_date":0,"end_date":null}"""
        )
        written.zip(onePerPattern).forEach { (json, pattern) ->
            assertTrue(json, passes(json))
            assertEquals(pattern, Json.decodeFromString(ScheduleConfig.serializer(), json).pattern)
        }
    }

    /**
     * A key the schedule does not declare is refused, at any depth: the Messages scheduler
     * would fail to read it and never send. That includes the switch a schedule no longer has.
     */
    @Test
    fun anUndeclaredKey_isRefused() {
        assertFalse(passes("""{"pattern":{"type":"DailyMultiple","times":["09:00"]},"enabled":true}"""))
        assertFalse(passes("""{"pattern":{"type":"DailyMultiple","times":["09:00"],"every":2}}"""))
        assertFalse(passes("""{"pattern":{"type":"WeeklyCustom","moments":[{"day_of_week":1,"time":"09:00","note":"x"}]}}"""))
    }

    /** The reader takes any text as a time; the schema is what keeps "9h" out. */
    @Test
    fun aMalformedTime_isRefused() {
        assertFalse(passes("""{"pattern":{"type":"DailyMultiple","times":["9h"]}}"""))
        assertFalse(passes("""{"pattern":{"type":"WeeklySimple","days_of_week":[8],"time":"09:00"}}"""))
        assertFalse(passes("""{"pattern":{"type":"DailyMultiple","times":[]}}"""))
    }
}
