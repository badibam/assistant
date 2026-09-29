package com.assistant.core.conditions

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.FilterOperator
import com.assistant.core.selection.TimeResolver
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A condition judged once compares two values as their field does, a missing value leaving it
 * unknown; its stored form reads back into sides, operator and the right count of right sides.
 */
class ConditionJudgeTest {

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 9, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val resolver = TimeResolver(at(16, 15), zone, dayStartHour = 4, weekStartDay = "MONDAY")
    private fun field(type: FieldType) = FieldDefinition("f", "F", null, type, false, null)
    private fun holds(type: FieldType, op: FilterOperator, left: Any?, vararg right: Any?) =
        ConditionJudge.holds(field(type), op, left, right.toList(), resolver) { it }

    @Test
    fun numbersAndDurations_compareAsNumbers_betweenIncluded() {
        assertEquals(true, holds(FieldType.DURATION, FilterOperator.GREATER_OR_EQUAL, 25_200_000L, 25_200_000))
        assertEquals(false, holds(FieldType.NUMERIC, FilterOperator.LESS_OR_EQUAL, 2300.0, 2100))
        assertEquals(true, holds(FieldType.NUMERIC, FilterOperator.BETWEEN, 80, 70, 80))
    }

    @Test
    fun aMissingValue_isUnknown_whileAbsentAndPresentSayIt() {
        assertNull(holds(FieldType.NUMERIC, FilterOperator.GREATER, null, 1))
        assertEquals(true, holds(FieldType.TEXT, FilterOperator.ABSENT, ""))
        assertEquals(false, holds(FieldType.TEXT, FilterOperator.PRESENT, null))
    }

    @Test
    fun choicesTextsBooleansAndHours_compareAsTheirFieldDoes() {
        assertEquals(true, holds(FieldType.CHOICE, FilterOperator.IN, listOf("good", "calm"), listOf("calm")))
        assertEquals(true, holds(FieldType.TEXT, FilterOperator.CONTAINS, "Grande Marche", "marche"))
        assertEquals(true, holds(FieldType.BOOLEAN, FilterOperator.EQUAL, true, true))
        assertEquals(true, holds(FieldType.TIME, FilterOperator.LESS, "9:05", "10:00"))
    }

    @Test
    fun aRelativeDate_resolvesAgainstTheReference() {
        val yesterday = JSONObject().put("relative", JSONObject().put("unit", "DAY").put("offset", -1).put("edge", "START"))
        assertEquals(true, holds(FieldType.DATETIME, FilterOperator.GREATER_OR_EQUAL, at(15, 12), yesterday))
        assertEquals(false, holds(FieldType.DATETIME, FilterOperator.GREATER_OR_EQUAL, at(14, 12), yesterday))
    }

    @Test
    fun theStoredForm_readsIntoSides() {
        val condition = Condition.fromJson(JSONObject("""{"left": {"variable": "v1"}, "op": "between", "right": [{"constant": 1}, {"reading": {"selection": {"target": {"kind": "TOOL_INSTANCE", "id": "t1"}}, "reduction": "COUNT"}}]}"""), "c") { it }
        assertTrue(condition.left is Condition.Side.Of)
        assertEquals(2, condition.right.size)
        assertTrue(Condition.fromJson(JSONObject("""{"left": {"field": "data.x"}, "op": "present"}"""), "c") { it }.left is Condition.Side.Field)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aBetweenWithOneBound_isRefused() {
        Condition.fromJson(JSONObject("""{"left": {"variable": "v1"}, "op": "between", "right": {"constant": 1}}"""), "c") { it }
    }

    @Test
    fun aDayCompares_asADay() {
        assertFalse(holds(FieldType.DATE, FilterOperator.LESS, "2026-09-16", "2026-09-15")!!)
    }
}
