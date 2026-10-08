package app.treelune.core.fields

import org.json.JSONObject

/**
 * Where a field of an entry lives: the tool type's fixed fields, or the user's.
 * Its key is the name of the entry's JSON object.
 */
enum class FieldContainer(val key: String) {
    DATA("data"),
    EXTRA("extra")
}

/**
 * A DURATION field "running": started, not stopped yet.
 *
 * The start instant is written in the entry's state, at state.running.<data|extra>.<field>, so
 * the truth is in the database and a stopwatch survives the app being killed. Stopping adds the
 * time elapsed to the field's value and removes the key. The time elapsed so far is never
 * stored: it is computed on reading, from the start instant and the clock.
 *
 * The same two operations serve every caller -- a tool's buttons, a form, the AI -- through the
 * dispatcher; this object only computes what they write.
 */
object RunningDurations {

    /** The instant [field] started, or null when it is not running. */
    fun startedAt(state: JSONObject?, container: FieldContainer, field: String): Long? =
        state?.optJSONObject(EntrySchemaGenerator.RUNNING_KEY)
            ?.optJSONObject(container.key)
            ?.takeIf { it.has(field) }
            ?.getLong(field)

    /** Whether any field of the entry is running, for the marker a screen or a filter shows. */
    fun anyRunning(state: JSONObject?): Boolean =
        state?.optJSONObject(EntrySchemaGenerator.RUNNING_KEY)?.let { running ->
            FieldContainer.entries.any { (running.optJSONObject(it.key)?.length() ?: 0) > 0 }
        } ?: false

    /**
     * The value to show for [field] at [now]: its stored value, plus the time since it started
     * if it is running.
     */
    fun currentValue(storedValue: Long?, state: JSONObject?, container: FieldContainer, field: String, now: Long): Long? {
        val start = startedAt(state, container, field) ?: return storedValue
        return (storedValue ?: 0L) + (now - start).coerceAtLeast(0L)
    }

    /**
     * The entry's state once [field] has started at [now].
     *
     * @throws IllegalStateException if it is already running: starting it again would lose the
     *         first start without saying so
     */
    fun start(state: JSONObject?, container: FieldContainer, field: String, now: Long): JSONObject {
        check(startedAt(state, container, field) == null) { "Duration field ${container.key}.$field is already running" }

        val next = JSONObject(state?.toString() ?: "{}")
        val running = next.optJSONObject(EntrySchemaGenerator.RUNNING_KEY) ?: JSONObject().also { next.put(EntrySchemaGenerator.RUNNING_KEY, it) }
        val inContainer = running.optJSONObject(container.key) ?: JSONObject().also { running.put(container.key, it) }
        inContainer.put(field, now)
        return next
    }

    /**
     * What stopping [field] at [now] writes: the state without its start instant, and the
     * field's new value, the stored one (none counts as zero) plus the time elapsed. Adding
     * rather than replacing lets a field be started again after a stop and keep counting.
     *
     * @throws IllegalStateException if it is not running
     */
    fun stop(state: JSONObject?, container: FieldContainer, field: String, storedValue: Long?, now: Long): Stopped {
        val start = checkNotNull(startedAt(state, container, field)) { "Duration field ${container.key}.$field is not running" }

        val next = JSONObject(state.toString())
        val running = next.getJSONObject(EntrySchemaGenerator.RUNNING_KEY)
        val inContainer = running.getJSONObject(container.key)
        inContainer.remove(field)
        // Nothing empty is left behind: no running field, no "running" key
        if (inContainer.length() == 0) running.remove(container.key)
        if (running.length() == 0) next.remove(EntrySchemaGenerator.RUNNING_KEY)

        return Stopped(state = next, value = (storedValue ?: 0L) + (now - start).coerceAtLeast(0L))
    }

    /** What a stop writes: the entry's [state] and the field's [value] in milliseconds. */
    data class Stopped(val state: JSONObject, val value: Long)
}
