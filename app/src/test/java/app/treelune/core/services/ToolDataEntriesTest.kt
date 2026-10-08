package app.treelune.core.services

import app.treelune.core.database.entities.ToolDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers an entry as the service hands it out, list and single read alike.
 *
 * The single read handed data out as its stored string, and the journal, which reads
 * data["content"], opened every existing entry with an empty text.
 */
class ToolDataEntriesTest {

    private val journalEntry = ToolDataEntity(
        id = "entry-1",
        toolInstanceId = "journal-1",
        tooltype = "journal",
        timestamp = 1790200000000L,
        name = "Morning",
        data = """{"content":"Slept well."}""",
        createdAt = 1790200000000L,
        updatedAt = 1790200000000L,
        extra = """{"mood":"calm"}"""
    )

    @Test
    fun dataIsAnObjectTheCallerCanRead() {
        val data = ToolDataEntries.toMap(journalEntry)["data"] as Map<*, *>
        assertEquals("Slept well.", data["content"])
    }

    @Test
    fun customFieldsAreAnObjectToo() {
        val customFields = ToolDataEntries.toMap(journalEntry)["extra"] as Map<*, *>
        assertEquals("calm", customFields["mood"])
    }

    /** Timestamps stay in milliseconds: ISO is for the model, and made where it is spoken to. */
    @Test
    fun timestampsStayInMilliseconds() {
        assertEquals(1790200000000L, ToolDataEntries.toMap(journalEntry)["timestamp"])
    }

    @Test
    fun anEntryWithoutCustomFieldsSaysSo() {
        assertNull(ToolDataEntries.toMap(journalEntry.copy(extra = null))["extra"])
    }
}
