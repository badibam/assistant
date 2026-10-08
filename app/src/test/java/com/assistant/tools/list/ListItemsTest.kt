package com.assistant.tools.list

import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.tools.ManualOrder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers how a list shows its items and where a dragged item lands (docs/design/missing-tools.md,
 * "Liste"): the items left in the manual order, the checked ones below in the order they were
 * checked, and a drop among the items left kept by the service's order although checked items
 * hold positions between them.
 */
class ListItemsTest {

    private fun item(id: String, position: Int, checkedAt: Long? = null) =
        ListItem(id = id, name = id, position = position, checkedAt = checkedAt, extra = emptyMap())

    /** The entry the service holds for [item], [position] written in its state when given. */
    private fun entity(item: ListItem, position: Int = item.position) = ToolDataEntity(
        id = item.id,
        toolInstanceId = "list-1",
        tooltype = "list",
        timestamp = 0,
        name = item.name,
        data = "{}",
        state = JSONObject().put(ManualOrder.POSITION, position).apply {
            item.checkedAt?.let { put(ListToolType.CHECKED_AT, it) }
        }.toString(),
        createdAt = item.position.toLong(),
        updatedAt = 0
    )

    /** The items left, in the order shown once [moved] is written at [position] and the service has settled. */
    private fun leftAfterMove(items: List<ListItem>, moved: ListItem, position: Int): List<String> {
        val entities = items.map { if (it.id == moved.id) entity(it, position) else entity(it) }
        val settled = ManualOrder.settle(entities, moved.id).associateBy { it.id }
        val positions = entities.associate { e -> e.id to JSONObject((settled[e.id] ?: e).state!!).getInt(ManualOrder.POSITION) }
        return ListItems.shown(items.map { it.copy(position = positions.getValue(it.id)) })
            .filterNot { it.isChecked }
            .map { it.id }
    }

    // a, c, e are left; b and d are checked, between them in the manual order
    private val items = listOf(
        item("a", 0), item("b", 1, checkedAt = 200), item("c", 2), item("d", 3, checkedAt = 100), item("e", 4)
    )
    private val left = ListItems.shown(items).filterNot { it.isChecked }

    @Test
    fun theItemsLeftComeFirstInTheirOrderThenTheCheckedOnesInTheOrderTheyWereChecked() {
        assertEquals(listOf("a", "c", "e", "d", "b"), ListItems.shown(items).map { it.id })
    }

    @Test
    fun anItemDroppedLowerLandsWhereItWasDropped() {
        assertEquals(listOf("c", "e", "a"), leftAfterMove(items, left[0], ListItems.positionForMove(left, 0, 2)))
        assertEquals(listOf("c", "a", "e"), leftAfterMove(items, left[0], ListItems.positionForMove(left, 0, 1)))
    }

    @Test
    fun anItemDroppedHigherLandsWhereItWasDropped() {
        assertEquals(listOf("e", "a", "c"), leftAfterMove(items, left[2], ListItems.positionForMove(left, 2, 0)))
        assertEquals(listOf("a", "e", "c"), leftAfterMove(items, left[2], ListItems.positionForMove(left, 2, 1)))
    }

    /** Unchecking forgets when, not where: the item goes back to its place among the ones left. */
    @Test
    fun anItemUncheckedGoesBackToItsPlace() {
        val unchecked = items.map { if (it.id == "b") it.copy(checkedAt = null) else it }
        assertEquals(listOf("a", "b", "c", "e", "d"), ListItems.shown(unchecked).map { it.id })
    }

    /** What a list set to remove what is checked removes from the items already there. */
    @Test
    fun onlyAnItemWithACheckedDate_isChecked() {
        assertEquals(true, ListItems.isChecked("""{"checked_at": 1760000000000, "position": 2}"""))
        assertEquals(false, ListItems.isChecked("""{"checked_at": null, "position": 2}"""))
        assertEquals(false, ListItems.isChecked("""{"position": 2}"""))
        assertEquals(false, ListItems.isChecked(null))
    }
}
