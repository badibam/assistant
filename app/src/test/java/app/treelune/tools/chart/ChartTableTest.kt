package app.treelune.tools.chart

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.ui.components.PeriodType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A layer's table turns without computing anything: a fold makes a row per column, a flatten a
 * row per option; a grid steps through the user's calendar, never past now.
 */
class ChartTableTest {

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(day: Int, hour: Int = 0, minute: Int = 0) = LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun numeric(name: String, unit: String = "kcal") = FieldDefinition(name, name, null, FieldType.NUMERIC, false, mapOf("unit" to unit))

    @Test
    fun `a fold gives a row per column folded, a failure kept in its cell`() {
        val failed = Cell.Failed("no entry", listOf("e1"))
        val table = ChartTable(
            columns = mapOf("timestamp" to FieldDefinition("timestamp", "t", null, FieldType.DATETIME, false, null), "food" to numeric("Food"), "empty" to numeric("Empty")),
            rows = listOf(Row(mapOf("timestamp" to Cell.Value(1L), "food" to Cell.Value(1800.0), "empty" to failed), span = 1L to 2L))
        )
        val folded = table.transformed(Transform.Fold(listOf("food", "empty"), "kind", "kcal"))
        assertEquals(setOf("timestamp", "kind", "kcal"), folded.columns.keys)
        assertEquals(FieldType.CHOICE, folded.columns.getValue("kind").type)
        assertEquals("kcal", folded.columns.getValue("kcal").config!!["unit"])
        assertEquals(2, folded.rows.size)
        assertEquals("food", folded.rows[0].value("kind"))
        assertEquals(1800.0, folded.rows[0].value("kcal"))
        assertEquals(failed, folded.rows[1].failure("kcal"))
        assertEquals(1L to 2L, folded.rows[1].span)
    }

    @Test
    fun `a flatten gives a row per option, a row without any kept`() {
        val tags = FieldDefinition("tags", "Tags", null, FieldType.CHOICE, false, mapOf("multiple" to true, "options" to listOf(mapOf("value" to "a"), mapOf("value" to "b"))))
        val table = ChartTable(mapOf("data.tags" to tags), listOf(
            Row(mapOf("data.tags" to Cell.Value(listOf("a", "b")))),
            Row(mapOf("data.tags" to Cell.Value(null)))
        ))
        val flat = table.transformed(Transform.Flatten(listOf("data.tags")))
        assertEquals(listOf("a", "b", null), flat.rows.map { it.value("data.tags") })
        assertTrue(flat.columns.getValue("data.tags").config!!["multiple"] == null)
    }

    @Test
    fun `a grid steps through days starting at the hour the user set, and stops at now`() {
        val steps = GridSteps.of(PeriodType.DAY, at(10, 12), at(20), now = at(12, 9), dayStartHour = 4, weekStartDay = "monday", zone = zone)
        // The 10th's day started at 4:00, the 12th's is the last begun
        assertEquals(listOf(at(10, 4), at(11, 4), at(12, 4)), steps.map { it.first })
        assertEquals(at(11, 4) - 1, steps.first().second)
    }

    @Test
    fun `weeks start on the day the user set`() {
        val steps = GridSteps.of(PeriodType.WEEK, at(9), at(22), now = at(30), dayStartHour = 0, weekStartDay = "monday", zone = zone)
        // The 9th of September 2026 is a Wednesday: its week began on Monday the 7th
        assertEquals(listOf(at(7), at(14), at(21)), steps.map { it.first })
        assertEquals(1, GridSteps.weekday(at(7), zone))
        assertEquals(7, GridSteps.weekday(at(13), zone))
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), GridSteps.weekOrder("sunday"))
    }
}
