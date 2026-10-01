package com.assistant.tools.list

import com.assistant.core.database.entities.ToolDataEntity
import org.json.JSONObject

/**
 * The rule between an item's due date and the mark of its notification: the mark names the due date notified, so it only holds while
 * that date is still the item's. A date moved or removed makes the mark false, and it goes; the
 * date is then due anew, or no longer at all.
 */
object DueNotice {

    /** [entry] without its mark when the mark no longer names its due date; null when it holds or there is none. */
    fun settle(entry: ToolDataEntity): ToolDataEntity? {
        val state = JSONObject(entry.state ?: return null)
        if (!state.has(ListToolType.DUE_NOTIFIED)) return null
        val data = JSONObject(entry.data)
        val dueAt = if (data.isNull(ListToolType.DUE_AT)) null else data.getLong(ListToolType.DUE_AT)
        if (dueAt == state.getLong(ListToolType.DUE_NOTIFIED)) return null
        state.remove(ListToolType.DUE_NOTIFIED)
        return entry.copy(state = state.toString())
    }

    /** The items whose due date has come at [now], not checked and not notified yet, oldest due first. */
    fun toNotify(items: List<ListItem>, now: Long): List<ListItem> =
        items.filter { item ->
            val dueAt = item.dueAt ?: return@filter false
            !item.isChecked && dueAt <= now && item.dueNotified != dueAt
        }.sortedBy { it.dueAt }

    /** Whether [item] is late at [now]: its due date passed and it is not checked. Shown, never stored. */
    fun isLate(item: ListItem, now: Long): Boolean =
        !item.isChecked && item.dueAt != null && item.dueAt <= now
}
