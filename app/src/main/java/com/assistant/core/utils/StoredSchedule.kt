package com.assistant.core.utils

import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * The recurrence a config holds under "schedule" (a Messages tool, a goal): none, readable, or unreadable.
 *
 * Unreadable is kept apart from none because the two call for opposite handling. No recurrence
 * means nothing is expected, and the scheduler sheds what an earlier recurrence generated. A
 * recurrence that fails to read -- data written outside the schema, an old backup -- says nothing
 * about what is expected: treated as none, it deleted the pending messages and let the config
 * screen save it away as empty.
 */
sealed interface StoredSchedule {
    object None : StoredSchedule
    data class Readable(val schedule: ScheduleConfig) : StoredSchedule
    /** [raw] is the stored JSON, kept so a save that does not redefine the recurrence writes it back. */
    data class Unreadable(val raw: JSONObject, val cause: String) : StoredSchedule

    companion object {
        /** Read the "schedule" of a config with the same strict reader as the schedulers. */
        fun of(config: JSONObject): StoredSchedule {
            val raw = config.optJSONObject("schedule") ?: return None
            return try {
                Readable(Json.decodeFromString(ScheduleConfig.serializer(), raw.toString()))
            } catch (e: Exception) {
                Unreadable(raw, e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
