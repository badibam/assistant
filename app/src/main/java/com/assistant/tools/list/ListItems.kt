package com.assistant.tools.list

import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.tools.ManualOrder
import org.json.JSONArray
import org.json.JSONObject

/**
 * One item of a list, as the screen and the tile read it.
 *
 * @property position Its place in the manual order, among every item, checked or not
 * @property checkedAt When it was checked, null while it is not
 * @property extra The list's own fields
 */
data class ListItem(
    val id: String,
    val name: String,
    val position: Int,
    val checkedAt: Long?,
    val extra: Map<String, Any?>
) {
    val isChecked: Boolean get() = checkedAt != null
}

/**
 * How a list reads, shows and changes its items, shared by its screen and its tile. Every write
 * goes through the coordinator, as the AI's would; the service keeps the manual order.
 */
object ListItems {

    /**
     * The items as a list shows them: those left in the manual order, then the checked ones in
     * the order they were checked. Each keeps its position among all, so an item unchecked goes
     * back to where it was.
     */
    fun shown(items: List<ListItem>): List<ListItem> {
        val (checked, left) = items.partition { it.isChecked }
        return left.sortedBy { it.position } + checked.sortedBy { it.checkedAt }
    }

    /**
     * The position to write for an item moved from [from] to [to] among [left], the items not
     * checked in their shown order: the position of the item it lands on when moving up, the one
     * after when moving down (ManualOrder puts a written item before the one holding its position).
     */
    fun positionForMove(left: List<ListItem>, from: Int, to: Int): Int =
        if (to < from) left[to].position else left[to].position + 1

    /** Every item of the tool, as the service hands them out. */
    suspend fun load(coordinator: Coordinator, toolInstanceId: String): List<ListItem>? {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId))
        if (!result.isSuccess) return null
        val entries = result.data?.get("entries") as? List<*> ?: return null
        return entries.map { entry ->
            val map = entry as Map<*, *>
            val state = map["state"] as? Map<*, *> ?: emptyMap<String, Any?>()
            @Suppress("UNCHECKED_CAST")
            ListItem(
                id = map["id"] as String,
                name = map["name"] as String,
                position = (state[ManualOrder.POSITION] as? Number)?.toInt() ?: Int.MAX_VALUE,
                checkedAt = (state[ListToolType.CHECKED_AT] as? Number)?.toLong(),
                extra = map["extra"] as? Map<String, Any?> ?: emptyMap()
            )
        }
    }

    /** Adds an item named [name]; without a position, the service puts it last. */
    suspend fun add(coordinator: Coordinator, toolInstanceId: String, name: String, extra: Map<String, Any?>) =
        coordinator.processUserAction("tool_data.create", buildMap {
            put("tool_instance_id", toolInstanceId)
            put("tooltype", "list")
            put("name", name.trim())
            if (extra.isNotEmpty()) put("extra", JSONObject(extra))
        })

    /** Renames [item] and sets its fields, those emptied sent as null to be cleared. */
    suspend fun update(coordinator: Coordinator, item: ListItem, name: String, extra: Map<String, Any?>) =
        coordinator.processUserAction("tool_data.update", mapOf(
            "id" to item.id,
            "name" to name.trim(),
            "extra" to com.assistant.core.fields.extraForUpdate(item.extra, extra)
        ))

    /**
     * Checks [item] now, or unchecks it, which forgets when it was checked. In a list set to
     * remove what is checked ([removeWhenChecked]), checking deletes the item instead.
     */
    suspend fun setChecked(coordinator: Coordinator, item: ListItem, checked: Boolean, removeWhenChecked: Boolean) =
        if (checked && removeWhenChecked) delete(coordinator, item)
        else coordinator.processUserAction("tool_data.update", mapOf(
            "id" to item.id,
            "state" to JSONObject().put(ListToolType.CHECKED_AT, if (checked) System.currentTimeMillis() else JSONObject.NULL)
        ))

    /** Puts [item] at [position] in the manual order; the service moves the others around it. */
    suspend fun move(coordinator: Coordinator, item: ListItem, position: Int) =
        coordinator.processUserAction("tool_data.update", mapOf(
            "id" to item.id,
            "state" to JSONObject().put(ManualOrder.POSITION, position)
        ))

    suspend fun delete(coordinator: Coordinator, item: ListItem) =
        coordinator.processUserAction("tool_data.delete", mapOf("id" to item.id))

    /** Unchecks every checked item, in one write: when they were checked is forgotten. */
    suspend fun uncheckAll(coordinator: Coordinator, items: List<ListItem>) =
        coordinator.processUserAction("tool_data.batch_update", mapOf(
            "entries" to JSONArray(items.filter { it.isChecked }.map { item ->
                JSONObject()
                    .put("id", item.id)
                    .put("state", JSONObject().put(ListToolType.CHECKED_AT, JSONObject.NULL))
            })
        ))
}
