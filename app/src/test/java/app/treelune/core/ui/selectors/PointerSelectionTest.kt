package app.treelune.core.ui.selectors

import app.treelune.core.conditions.Conditions
import app.treelune.core.selection.Edge
import app.treelune.core.selection.EntryPeriod
import app.treelune.core.selection.ReferenceKind
import app.treelune.core.selection.TimePoint
import app.treelune.core.ui.components.PeriodType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the pointer selector stores: the deepest place reached by its id, what is attached, and
 * for a tool its entries narrowed; what going up or into another place leaves behind.
 */
class PointerSelectionTest {

    private val health = Named("z1", "Health")
    private val sleep = Named("t1", "Sleep", "tracking")
    private val shortNights = JSONArray().put(Conditions.onField("data.duration", "<", 21600000))
    private val lastWeek = EntryPeriod(TimePoint.Relative(PeriodType.DAY, -7, Edge.START), TimePoint.Now)

    @Test
    fun `nothing can be pointed at before a zone is reached`() {
        assertFalse(PointerSelection().complete)
        assertTrue(PointerSelection(SelectionDraft().intoZone(health)).complete)
    }

    @Test
    fun `a tool is pointed at by its id, its period apart from its filters`() {
        val selection = PointerSelection(SelectionDraft().intoZone(health).intoTool(sleep).copy(
            period = lastWeek,
            filters = shortNights,
            fields = listOf("data.duration")
        ), entries = true)
        val pointer = selection.pointer()
        assertEquals(ReferenceKind.TOOL_INSTANCE, pointer.target.kind)
        assertEquals("t1", pointer.target.id)
        assertTrue(pointer.entries)
        assertEquals(TimePoint.Relative(PeriodType.DAY, -7, Edge.START), pointer.selection.period.start)
        assertEquals(TimePoint.Now, pointer.selection.period.end)
        assertEquals(1, pointer.selection.filters.length())
        assertEquals("data.duration", Conditions.fieldOf(pointer.selection.filters.getJSONObject(0)))
        assertEquals(listOf("data.duration"), pointer.selection.fields)
    }

    @Test
    fun `a zone attaches its config and its tools' entries over a period`() {
        val pointer = PointerSelection(SelectionDraft().intoZone(health).copy(period = EntryPeriod(end = TimePoint.Now)), config = true, entries = true).pointer()
        assertEquals(ReferenceKind.ZONE, pointer.target.kind)
        assertEquals("z1", pointer.target.id)
        assertTrue(pointer.config)
        assertTrue(pointer.entries)
        assertEquals(TimePoint.Now, pointer.selection.period.end)
        assertEquals(0, pointer.selection.filters.length())
        assertNull(pointer.selection.problem { it })
    }

    @Test
    fun `nothing ticked is a mention, narrowed or not`() {
        val selection = PointerSelection(SelectionDraft().intoZone(health).intoTool(sleep).copy(filters = shortNights))
        assertTrue(selection.pointer().isMention)
        assertTrue(selection.draft.narrowed)
    }

    @Test
    fun `going back up to the zone drops what only the tool offered`() {
        val up = SelectionDraft().intoZone(health).intoTool(sleep)
            .copy(filters = shortNights, fields = listOf("data.duration"), period = EntryPeriod(end = TimePoint.Now))
            .upTo(ReferenceKind.ZONE)
        assertEquals(ReferenceKind.ZONE, up.level)
        assertEquals(TimePoint.Now, up.period.end)
        assertEquals(0, up.filters.length())
        assertNull(up.fields)
    }

    @Test
    fun `another tool keeps the period but not the filters`() {
        val other = SelectionDraft().intoZone(health).intoTool(sleep)
            .copy(filters = shortNights, period = EntryPeriod(end = TimePoint.Now))
            .intoTool(Named("t2", "Mood", "tracking"))
        assertEquals(0, other.filters.length())
        assertEquals(TimePoint.Now, other.period.end)
    }

    @Test
    fun `the selection survives a rotation whole`() {
        val selection = PointerSelection(SelectionDraft().intoZone(health).intoTool(sleep).copy(
            period = EntryPeriod(TimePoint.Fixed(1000L), TimePoint.Now),
            filters = shortNights,
            fields = listOf("data.duration")
        ), config = true, entries = true)
        val restored = PointerSelection.fromJson(selection.toJson())
        assertEquals(selection.draft.zone, restored.draft.zone)
        assertEquals(selection.draft.tool, restored.draft.tool)
        assertEquals(selection.draft.period.toJson().toString(), restored.draft.period.toJson().toString())
        assertEquals(selection.draft.fields, restored.draft.fields)
        assertEquals(selection.draft.filters.toString(), restored.draft.filters.toString())
        assertEquals(selection.pointer().toJson().toString(), restored.pointer().toJson().toString())
    }

    @Test
    fun `a period on a date field is a start and an end on that field, each only when set`() {
        val filters = periodConditions("extra.due", EntryPeriod(start = TimePoint.Relative(PeriodType.WEEK, 0, Edge.START)))
        assertEquals(1, filters.length())
        assertEquals("extra.due", Conditions.fieldOf(filters.getJSONObject(0)))
        assertEquals(">=", filters.getJSONObject(0).getString("op"))
        assertEquals("START", filters.getJSONObject(0).getJSONObject("right").getJSONObject("constant").getJSONObject("relative").getString("edge"))
    }

    @Test
    fun `a fixed bound keeps its field's stored form, now stays now`() {
        val filters = periodConditions("extra.due", EntryPeriod(TimePoint.Fixed("2026-09-15"), TimePoint.Now))
        assertEquals("2026-09-15", filters.getJSONObject(0).getJSONObject("right").getString("constant"))
        assertEquals("<=", filters.getJSONObject(1).getString("op"))
        assertEquals("NOW", filters.getJSONObject(1).getJSONObject("right").getJSONObject("constant").getString("relative"))
    }

    @Test
    fun `the browser moving to another zone's tool keeps the period, not the conditions`() {
        val work = Named("z2", "Work")
        val tasks = Named("t9", "Tasks", "list")
        val moved = SelectionDraft().intoZone(health).intoTool(sleep)
            .copy(period = lastWeek, filters = shortNights, fields = listOf("data.duration"))
            .at(ThingPath(work, tasks))
        assertEquals(work, moved.zone)
        assertEquals(tasks, moved.tool)
        assertEquals(lastWeek, moved.period)
        assertEquals(0, moved.filters.length())
        assertNull(moved.fields)
    }

    @Test
    fun `the browser staying on the same tool changes nothing, going up to the app keeps nothing`() {
        val draft = SelectionDraft().intoZone(health).intoTool(sleep).copy(filters = shortNights)
        assertEquals(draft.toJson().toString(), draft.at(draft.path).toJson().toString())
        assertEquals(SelectionDraft().toJson().toString(), draft.at(ThingPath()).toJson().toString())
    }
}
