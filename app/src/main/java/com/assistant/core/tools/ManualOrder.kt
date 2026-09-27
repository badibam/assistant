package com.assistant.core.tools

import com.assistant.core.database.entities.ToolDataEntity
import org.json.JSONObject

/**
 * The manual order of the entries of one tool instance: positions 0, 1, 2… with no gap and no
 * tie, kept under "position" in each entry's state. For a tool type whose entries the user
 * orders by hand (notes, list items), from its settleEntries.
 *
 * Writing position p puts the entry at p: an entry written at a position already taken goes
 * before the one there, which moves down. An entry without a position goes last. Kept by the
 * service after every write, so the screen and the AI get the same order.
 */
object ManualOrder {

    /** The state key the position is kept under. */
    const val POSITION = "position"

    /** The entries whose position must change for the order to hold, [writtenId] placed first on a tie. */
    fun settle(entries: List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> {
        val ordered = entries.sortedWith(
            compareBy<ToolDataEntity> { positionOf(it) ?: Int.MAX_VALUE }
                .thenBy { if (it.id == writtenId) 0 else 1 }
                .thenBy { it.createdAt }
        )

        return ordered.mapIndexedNotNull { index, entry ->
            if (positionOf(entry) == index) null
            else entry.copy(state = JSONObject(entry.state ?: "{}").put(POSITION, index).toString())
        }
    }

    private fun positionOf(entry: ToolDataEntity): Int? {
        val state = JSONObject(entry.state ?: return null)
        return if (state.has(POSITION)) state.getInt(POSITION) else null
    }
}
