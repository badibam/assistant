package com.assistant.tools.tracking

import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.fields.FieldContainer
import com.assistant.core.fields.RunningDurations
import org.json.JSONObject

/**
 * One activity at a time in a tracking tool: starting one stops the one running.
 *
 * Kept by the service after every write (settleEntries), so it holds whoever starts the
 * stopwatch -- the tool's buttons or the AI. The one stopped is stopped at the instant the new
 * one started, so no time is counted twice.
 */
object TrackingStopwatch {

    private const val FIELD = "value"

    /** The entries to stop because [writtenId] is running now, each with its time added. */
    fun settle(entries: List<ToolDataEntity>, writtenId: String?): List<ToolDataEntity> {
        val written = entries.firstOrNull { it.id == writtenId } ?: return emptyList()
        val startedAt = RunningDurations.startedAt(stateOf(written), FieldContainer.DATA, FIELD) ?: return emptyList()

        return entries
            .filter { it.id != writtenId && RunningDurations.startedAt(stateOf(it), FieldContainer.DATA, FIELD) != null }
            .map { entry ->
                val data = JSONObject(entry.data)
                val stored = if (data.has(FIELD)) data.getLong(FIELD) else null
                val stopped = RunningDurations.stop(stateOf(entry), FieldContainer.DATA, FIELD, stored, startedAt)
                entry.copy(
                    data = data.put(FIELD, stopped.value).toString(),
                    state = stopped.state.takeIf { it.length() > 0 }?.toString()
                )
            }
    }

    private fun stateOf(entry: ToolDataEntity): JSONObject? = entry.state?.let { JSONObject(it) }
}
