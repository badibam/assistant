package app.treelune.core.grid

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers a list of groups changing: what its elements hold after, and what it would leave them without. */
class GroupsChangeTest {

    @Test
    fun aRenamedGroupIsHeldUnderItsNewName() {
        val change = Groups.Change(listOf("Body", "Work"), listOf("Health", "Work"), mapOf("Body" to "Health"))

        assertTrue(change.isValid)
        assertEquals("Health", change.held("Body"))
        assertEquals("Work", change.held("Work"))
        assertNull(change.held(null))
        assertFalse("a renamed group is not lost", change.loses("Body"))
        assertEquals(listOf("Health", "Work"), change.beforeRenamed)
    }

    @Test
    fun aRemovedGroupIsLostByWhatHoldsIt() {
        val change = Groups.Change(listOf("Body", "Work"), listOf("Work"), emptyMap())

        assertTrue(change.loses("Body"))
        assertFalse(change.loses("Work"))
        assertFalse("nothing held, nothing lost", change.loses(null))
    }

    @Test
    fun aRemovalThenAnAdditionOfTheSameNameIsNotARename() {
        // The editor gives no rename for it: the group is still there, under its name
        val change = Groups.Change(listOf("Body"), listOf("Body"), emptyMap())

        assertFalse(change.loses("Body"))
    }

    @Test
    fun twoNamesSwappedAreBothFollowed() {
        val change = Groups.Change(listOf("A", "B"), listOf("B", "A"), mapOf("A" to "B", "B" to "A"))

        assertTrue(change.isValid)
        assertEquals("B", change.held("A"))
        assertEquals("A", change.held("B"))
        assertEquals("each element keeps its section", listOf("B", "A"), change.beforeRenamed)
    }

    @Test
    fun aRenameOutsideTheListsIsInvalid() {
        assertFalse(Groups.Change(listOf("A"), listOf("B"), mapOf("X" to "B")).isValid)
        assertFalse(Groups.Change(listOf("A"), listOf("B"), mapOf("A" to "C")).isValid)
    }

    @Test
    fun theRenamesOfAListAreReadFromTheParams() {
        val params = JSONObject("""{ "renames": { "tool_groups": { "Food": "Meals" } } }""")

        assertEquals(mapOf("Food" to "Meals"), Groups.renames(params, "tool_groups"))
        assertEquals(emptyMap<String, String>(), Groups.renames(params, "zone_groups"))
    }
}
