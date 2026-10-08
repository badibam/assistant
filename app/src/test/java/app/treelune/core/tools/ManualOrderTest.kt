package app.treelune.core.tools

import app.treelune.core.database.entities.ToolDataEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the manual order of a tool's entries (notes, list items), kept by the service after
 * every write.
 *
 * Adding above a note gave the new one the same position, and the swap used to move a note
 * exchanged two equal positions: the note could not climb above the first ones.
 */
class ManualOrderTest {

    private fun note(id: String, position: Int?, createdAt: Long) = ToolDataEntity(
        id = id,
        toolInstanceId = "notes-1",
        tooltype = "notes",
        timestamp = createdAt,
        name = null,
        data = JSONObject().put("content", id).toString(),
        state = position?.let { JSONObject().put("position", it).toString() },
        createdAt = createdAt,
        updatedAt = createdAt
    )

    /** The order the list shows once the settled notes are applied. */
    private fun orderAfter(entries: List<ToolDataEntity>, writtenId: String?): List<String> {
        val settled = ManualOrder.settle(entries, writtenId).associateBy { it.id }
        return entries.map { settled[it.id] ?: it }
            .sortedBy { JSONObject(it.state!!).getInt("position") }
            .map { it.id }
    }

    @Test
    fun aNoteWrittenAtATakenPositionGoesBeforeTheNoteThere() {
        val notes = listOf(note("a", 0, 1), note("b", 1, 2), note("new", 0, 3))
        assertEquals(listOf("new", "a", "b"), orderAfter(notes, "new"))
    }

    @Test
    fun movingUpTakesThePositionOfTheNoteAbove() {
        val notes = listOf(note("a", 0, 1), note("b", 0, 2), note("c", 2, 3))
        assertEquals(listOf("b", "a", "c"), orderAfter(notes, "b"))
    }

    @Test
    fun movingDownTakesThePositionAfterTheNoteBelow() {
        val notes = listOf(note("a", 2, 1), note("b", 1, 2), note("c", 2, 3))
        assertEquals(listOf("b", "a", "c"), orderAfter(notes, "a"))
    }

    @Test
    fun tiedNotesLeftByOlderWritesAreSeparatedByCreation() {
        val notes = listOf(note("late", 0, 5), note("early", 0, 1))
        assertEquals(listOf("early", "late"), orderAfter(notes, null))
    }

    @Test
    fun aNoteWithoutPositionGoesLast() {
        val notes = listOf(note("a", 0, 1), note("b", 1, 2), note("ai", null, 3))
        assertEquals(listOf("a", "b", "ai"), orderAfter(notes, "ai"))
    }

    @Test
    fun aDeleteClosesTheGap() {
        val settled = ManualOrder.settle(listOf(note("a", 0, 1), note("c", 2, 3)), null)
        assertEquals(listOf("c"), settled.map { it.id })
        assertEquals(1, JSONObject(settled.single().state!!).getInt("position"))
    }

    @Test
    fun anOrderThatHoldsChangesNothing() {
        val notes = listOf(note("a", 0, 1), note("b", 1, 2))
        assertEquals(emptyList<ToolDataEntity>(), ManualOrder.settle(notes, "b"))
    }
}
