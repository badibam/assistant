package com.assistant.tools.notes

import com.assistant.core.database.entities.ToolDataEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the manual order of notes, kept by the service after every write.
 *
 * Adding above a note gave the new one the same position, and the swap used to move a note
 * exchanged two equal positions: the note could not climb above the first ones.
 */
class NoteOrderTest {

    private fun note(id: String, position: Int?, createdAt: Long) = ToolDataEntity(
        id = id,
        toolInstanceId = "notes-1",
        tooltype = "notes",
        timestamp = createdAt,
        name = "Note",
        data = JSONObject().put("content", id).apply { if (position != null) put("position", position) }.toString(),
        createdAt = createdAt,
        updatedAt = createdAt
    )

    /** The order the list shows once the settled notes are applied. */
    private fun orderAfter(entries: List<ToolDataEntity>, writtenId: String?): List<String> {
        val settled = NoteOrder.settle(entries, writtenId).associateBy { it.id }
        return entries.map { settled[it.id] ?: it }
            .sortedBy { JSONObject(it.data).getInt("position") }
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
        val settled = NoteOrder.settle(listOf(note("a", 0, 1), note("c", 2, 3)), null)
        assertEquals(listOf("c"), settled.map { it.id })
        assertEquals(1, JSONObject(settled.single().data).getInt("position"))
    }

    @Test
    fun anOrderThatHoldsChangesNothing() {
        val notes = listOf(note("a", 0, 1), note("b", 1, 2))
        assertEquals(emptyList<ToolDataEntity>(), NoteOrder.settle(notes, "b"))
    }
}
