package com.assistant.tools.notes

import com.assistant.core.database.entities.ToolDataEntity
import org.json.JSONObject

/**
 * The manual order of the notes of one tool instance: positions 0, 1, 2… with no gap and no tie.
 *
 * The position lives in the entry's state. Writing position p puts the note at p: a note written at a position already taken goes before
 * the one there, which moves down. A note without a position goes last. Kept by the service after
 * every write, so the screen and the AI get the same order.
 */
object NoteOrder {

    /** The notes whose position must change for the order to hold, [writtenId] placed first on a tie. */
    fun settle(entries: List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> {
        val ordered = entries.sortedWith(
            compareBy<ToolDataEntity> { positionOf(it) ?: Int.MAX_VALUE }
                .thenBy { if (it.id == writtenId) 0 else 1 }
                .thenBy { it.createdAt }
        )

        return ordered.mapIndexedNotNull { index, entry ->
            if (positionOf(entry) == index) null
            else entry.copy(state = JSONObject(entry.state ?: "{}").put("position", index).toString())
        }
    }

    private fun positionOf(entry: ToolDataEntity): Int? {
        val state = JSONObject(entry.state ?: return null)
        return if (state.has("position")) state.getInt("position") else null
    }
}
