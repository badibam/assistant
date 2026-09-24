package com.assistant.core.versioning

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the renaming the 29 -> 30 migration and the backup import both apply: the two default
 * icons the app gave under names Lucide never had become the icons the tooltypes now default to.
 */
class FormerDefaultIconsTest {

    @Test
    fun theFormerDefaultsBecomeLucideNames() {
        val notes = JSONObject("""{ "name": "Notes", "icon_name": "note" }""")
        val messages = JSONObject("""{ "name": "Reminders", "icon_name": "notification" }""")

        assertTrue(FormerDefaultIcons.rename(notes))
        assertTrue(FormerDefaultIcons.rename(messages))
        assertEquals("sticky-note", notes.getString("icon_name"))
        assertEquals("bell", messages.getString("icon_name"))
    }

    /** Any other name is left as it is, and so is a config with no icon. */
    @Test
    fun anythingElseIsLeftAlone() {
        val chosen = JSONObject("""{ "icon_name": "scale" }""")
        val none = JSONObject("""{ "name": "No icon" }""")

        assertFalse(FormerDefaultIcons.rename(chosen))
        assertFalse(FormerDefaultIcons.rename(none))
        assertEquals("scale", chosen.getString("icon_name"))
        assertFalse(none.has("icon_name"))
    }
}
