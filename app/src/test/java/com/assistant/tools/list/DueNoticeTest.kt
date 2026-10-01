package com.assistant.tools.list

import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.tools.ManualOrder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the due dates of a list: which items the scheduler notifies, the mark of a notification
 * cleared when the due date it names is no longer the item's, and a list without due dates, a
 * stored one without the setting included, declaring none and leaving nothing waiting.
 */
class DueNoticeTest {

    private val now = 1_000_000L

    private fun item(id: String, dueAt: Long?, checkedAt: Long? = null, dueNotified: Long? = null) =
        ListItem(id = id, name = id, position = 0, checkedAt = checkedAt, extra = emptyMap(), dueAt = dueAt, dueNotified = dueNotified)

    private fun entity(id: String, dueAt: Long?, dueNotified: Long?, position: Int = 0) = ToolDataEntity(
        id = id,
        toolInstanceId = "list-1",
        tooltype = "list",
        timestamp = 0,
        name = id,
        data = JSONObject().apply { dueAt?.let { put(ListToolType.DUE_AT, it) } }.toString(),
        state = JSONObject().put(ManualOrder.POSITION, position).apply { dueNotified?.let { put(ListToolType.DUE_NOTIFIED, it) } }.toString(),
        createdAt = position.toLong(),
        updatedAt = 0
    )

    @Test
    fun `a due date passed, unchecked and not notified is notified, oldest first`() {
        val items = listOf(item("later", now - 10), item("earlier", now - 500), item("now", now))
        assertEquals(listOf("earlier", "later", "now"), DueNotice.toNotify(items, now).map { it.id })
    }

    @Test
    fun `a due date notified, checked, future or absent is not notified`() {
        val items = listOf(
            item("notified", now - 10, dueNotified = now - 10),
            item("checked", now - 10, checkedAt = now - 5),
            item("future", now + 1),
            item("none", null)
        )
        assertTrue(DueNotice.toNotify(items, now).isEmpty())
    }

    @Test
    fun `a due date moved after its notification is notified anew`() {
        assertEquals(listOf("moved"), DueNotice.toNotify(listOf(item("moved", now - 10, dueNotified = now - 900)), now).map { it.id })
    }

    @Test
    fun `moving or removing the due date of a notified item clears its mark`() {
        val moved = DueNotice.settle(entity("a", dueAt = now + 3600, dueNotified = now))!!
        assertFalse(JSONObject(moved.state!!).has(ListToolType.DUE_NOTIFIED))
        val removed = DueNotice.settle(entity("b", dueAt = null, dueNotified = now))!!
        assertFalse(JSONObject(removed.state!!).has(ListToolType.DUE_NOTIFIED))
    }

    @Test
    fun `a write that leaves the due date as notified keeps the mark`() {
        assertNull(DueNotice.settle(entity("a", dueAt = now, dueNotified = now)))
        assertNull(DueNotice.settle(entity("b", dueAt = now, dueNotified = null)))
    }

    @Test
    fun `the list settles its order and the mark of the item written in one answer`() {
        val written = entity("a", dueAt = now + 60, dueNotified = now, position = 5)
        val other = entity("b", dueAt = null, dueNotified = null, position = 1)
        val settled = kotlinx.coroutines.runBlocking { ListToolType.settleEntries({ listOf(written, other) }, "a") }.associateBy { it.id }
        val state = JSONObject(settled.getValue("a").state!!)
        assertFalse(state.has(ListToolType.DUE_NOTIFIED))
        assertEquals(1, state.getInt(ManualOrder.POSITION))
        assertEquals(0, JSONObject(settled.getValue("b").state!!).getInt(ManualOrder.POSITION))
    }

    @Test
    fun `a list has due dates only once turned on, a list stored without the setting having none`() {
        assertFalse(ListToolType.hasDueDates(JSONObject()))
        assertFalse(ListToolType.hasDueDates(JSONObject().put(ListToolType.DUE_DATES, false)))
        assertTrue(ListToolType.hasDueDates(JSONObject().put(ListToolType.DUE_DATES, true)))
    }

    @Test
    fun `nothing waits in a list without due dates, and a notified unchecked item waits in one`() {
        assertTrue(ListToolType.getWaiting(JSONObject()).isEmpty())
        assertTrue(ListToolType.getWaiting(JSONObject().put(ListToolType.DUE_DATES, false)).isEmpty())
        val conditions = ListToolType.getWaiting(JSONObject().put(ListToolType.DUE_DATES, true)).map { it.toString() }
        assertEquals(2, conditions.size)
        assertTrue(conditions.any { "state.${ListToolType.DUE_NOTIFIED}" in it && "present" in it })
        assertTrue(conditions.any { "state.${ListToolType.CHECKED_AT}" in it && "absent" in it })
    }

    @Test
    fun `late is a due date passed on an unchecked item`() {
        assertTrue(DueNotice.isLate(item("a", now - 1), now))
        assertFalse(DueNotice.isLate(item("b", now - 1, checkedAt = now - 2), now))
        assertFalse(DueNotice.isLate(item("c", now + 1), now))
        assertFalse(DueNotice.isLate(item("d", null), now))
    }
}
