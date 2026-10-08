package app.treelune.core.fields.settings

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the names a list's elements came with, followed through what its editor does to them. */
class ListOriginsTest {

    private fun followed(vararg values: String) = ListOrigins().apply { start("groups", values.toList()) }

    @Test
    fun anElementWhoseTextChangesIsRenamed() {
        val origins = followed("Body", "Work")

        assertEquals(mapOf("Body" to "Health"), origins.renames("groups", JSONArray(listOf("Health", "Work"))))
    }

    @Test
    fun anElementMovedKeepsItsName() {
        val origins = followed("Body", "Work").apply { moved("groups", listOf(1, 0)) }

        assertEquals("a move alone renames nothing", emptyMap<String, String>(), origins.renames("groups", JSONArray(listOf("Work", "Body"))))
        assertEquals(mapOf("Body" to "Health"), origins.renames("groups", JSONArray(listOf("Work", "Health"))))
    }

    @Test
    fun aRemovalThenAnAdditionOfTheSameNameIsNoRename() {
        val origins = followed("Body", "Work").apply {
            removed("groups", 0)
            added("groups")
        }

        assertEquals(emptyMap<String, String>(), origins.renames("groups", JSONArray(listOf("Work", "Body"))))
    }

    @Test
    fun aNewElementOrAnEmptiedOneRenamesNothing() {
        val origins = followed("Body").apply { added("groups") }

        assertEquals(emptyMap<String, String>(), origins.renames("groups", JSONArray(listOf(JSONObject.NULL, "Sport"))))
    }

    @Test
    fun twoNamesSwappedGiveTwoRenames() {
        val origins = followed("A", "B")

        assertEquals(mapOf("A" to "B", "B" to "A"), origins.renames("groups", JSONArray(listOf("B", "A"))))
    }

    @Test
    fun aListChangedElsewhereGivesNoRename() {
        val origins = followed("Body")

        assertEquals(emptyMap<String, String>(), origins.renames("groups", JSONArray(listOf("Health", "Work"))))
        assertEquals("a list not followed", emptyMap<String, String>(), origins.renames("other", JSONArray(listOf("X"))))
    }

    @Test
    fun startingAgainKeepsWhatIsFollowed() {
        val origins = followed("Body").apply { start("groups", listOf("Health")) }

        assertEquals(mapOf("Body" to "Health"), origins.renames("groups", JSONArray(listOf("Health"))))
    }
}
