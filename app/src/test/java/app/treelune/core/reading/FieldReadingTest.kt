package app.treelune.core.reading

import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.selection.Edge
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.selection.EntrySelection
import app.treelune.core.selection.Reference
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.selection.TimePoint
import app.treelune.core.selection.TimeResolver
import app.treelune.core.ui.components.PeriodType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The core's reading: a field reduced across entries keeps its type, a count is a whole number,
 * and no value is ever made up -- no entry, or an entry without an answer, is a failure that
 * names what to correct, except for a sum and a count, which are 0.
 */
class FieldReadingTest {

    private fun field(name: String, type: FieldType, config: Map<String, Any>? = null) =
        FieldDefinition(name, name, null, type, false, config)

    private val weight = field("weight", FieldType.NUMERIC, mapOf("decimals" to 1, "unit" to "kg"))
    private val sleep = field("sleep", FieldType.DURATION)
    private val mood = field("mood", FieldType.SCALE, mapOf("min" to 1, "max" to 10))
    private val bedtime = field("bedtime", FieldType.TIME)

    /** Entries newest first, as tool_data hands them out. */
    private fun entry(id: String, data: Map<String, Any?>) = mapOf("id" to id, "data" to data)

    private val entries = listOf(
        entry("c", mapOf("weight" to 80.0, "sleep" to 25_200_000L, "mood" to 6, "bedtime" to "23:30")),
        entry("b", mapOf("weight" to 81.5, "sleep" to 21_600_000L, "mood" to 7, "bedtime" to "9:05")),
        entry("a", mapOf("weight" to 83.0, "sleep" to 28_800_000L, "mood" to 8, "bedtime" to "22:00"))
    )

    private fun value(path: String?, field: FieldDefinition?, reduction: Reduction, rows: List<Map<String, Any?>> = entries): Any =
        (FieldReading.reduce(rows, path, field, reduction, 0L) as ReadingResult.Value).value

    @Test
    fun `the last is the newest, a sum and an average run over all`() {
        assertEquals(80.0, value("data.weight", weight, Reduction.LAST))
        assertEquals(244.5, value("data.weight", weight, Reduction.SUM))
        assertEquals(81.5, value("data.weight", weight, Reduction.AVERAGE))
        assertEquals(83.0, value("data.weight", weight, Reduction.MAX))
    }

    @Test
    fun `a duration stays whole milliseconds, a scale average keeps its scale`() {
        assertEquals(75_600_000L, value("data.sleep", sleep, Reduction.SUM))
        assertEquals(25_200_000L, value("data.sleep", sleep, Reduction.AVERAGE))
        val average = FieldReading.reduce(entries, "data.mood", mood, Reduction.AVERAGE, 0L) as ReadingResult.Value
        assertEquals(7.0, average.value)
        assertEquals(mood, average.field)
    }

    @Test
    fun `hours compare by time of day`() {
        assertEquals("9:05", value("data.bedtime", bedtime, Reduction.EARLIEST))
        assertEquals("23:30", value("data.bedtime", bedtime, Reduction.LATEST))
    }

    @Test
    fun `a count is a whole number, and zero without entries, as is a sum`() {
        val count = FieldReading.reduce(entries, null, null, Reduction.COUNT, 0L) as ReadingResult.Value
        assertEquals(3, count.value)
        assertEquals(FieldReading.COUNT_FIELD, count.field)
        assertEquals(0, value(null, null, Reduction.COUNT, emptyList()))
        assertEquals(0L, value("data.sleep", sleep, Reduction.SUM, emptyList()))
    }

    @Test
    fun `without an entry, anything but a sum or a count fails`() {
        val failure = FieldReading.reduce(emptyList(), "data.weight", weight, Reduction.LAST, 0L) as ReadingResult.Failure
        assertEquals(FailureReason.NO_ENTRY, failure.reason)
        assertEquals("data.weight", failure.field)
    }

    @Test
    fun `an entry without an answer fails the reading and is named`() {
        val rows = entries + entry("z", mapOf("mood" to 5)) + entry("y", mapOf("weight" to ""))
        val failure = FieldReading.reduce(rows, "data.weight", weight, Reduction.SUM, 0L) as ReadingResult.Failure
        assertEquals(FailureReason.MISSING_VALUE, failure.reason)
        assertEquals(listOf("z", "y"), failure.entries)
    }

    @Test
    fun `a reduction a type does not take is refused`() {
        assertThrows(IllegalArgumentException::class.java) { FieldReading.reduce(entries, "data.mood", mood, Reduction.SUM, 0L) }
        assertThrows(IllegalArgumentException::class.java) { FieldReading.reduce(entries, "data.bedtime", bedtime, Reduction.AVERAGE, 0L) }
        assertTrue(Reduction.forType(FieldType.RANGE).isEmpty())
    }

    @Test
    fun `a selection reads with its period first and its relative dates resolved, each by its field's type`() {
        val zone = ZoneId.of("Europe/Paris")
        fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 9, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
        val resolver = TimeResolver(at(16, 15), zone, dayStartHour = 4, weekStartDay = "MONDAY")
        val selection = EntrySelection(
            target = Reference(ReferenceKind.TOOL_INSTANCE, "t1"),
            period = EntryPeriod(start = TimePoint.Relative(PeriodType.DAY, -1, Edge.START)),
            filters = JSONArray()
                .put(Conditions.onField("extra.due", "<=", TimePoint.Now.toJson()))
                .put(Conditions.onField("data.weight", ">", 80))
        )
        val fields = mapOf("extra.due" to field("due", FieldType.DATE), "data.weight" to weight)
        val stored = selection.storedFilters(fields, resolver) { it }
        assertEquals(3, stored.length())
        fun written(i: Int) = stored.getJSONObject(i).getJSONObject("right").get("constant")
        assertEquals(at(15, 4), (written(0) as Number).toLong())
        assertEquals("2026-09-16", written(1))
        assertEquals(80, written(2))
    }

    @Test
    fun `a duration running counts up to the instant read`() {
        val running = mapOf("id" to "r", "data" to mapOf("sleep" to 3_600_000L), "state" to mapOf("running" to mapOf("data" to mapOf("sleep" to 10_000L))))
        assertEquals(3_600_000L + 50_000L, (FieldReading.reduce(listOf(running), "data.sleep", sleep, Reduction.SUM, 60_000L) as ReadingResult.Value).value)
    }
}
