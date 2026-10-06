package com.assistant.core.themes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An icon's colour (docs/design/icon-colors.md): the tags' names but grey, or none. */
class IconColorTest {

    @Test
    fun `the names are the tags' but grey, in their order`() {
        assertEquals(listOf("RED", "ORANGE", "YELLOW", "GREEN", "TEAL", "BLUE", "PURPLE", "PINK"), IconColor.NAMES)
    }

    @Test
    fun `a name of the list or none is accepted`() {
        IconColor.NAMES.forEach { assertTrue(it, IconColor.accepts(it)) }
        assertTrue(IconColor.accepts(null))
        assertTrue(IconColor.accepts(""))
    }

    @Test
    fun `grey, a name of no tag or one in lower case is refused`() {
        assertFalse(IconColor.accepts("GREY"))
        assertFalse(IconColor.accepts("CYAN"))
        assertFalse(IconColor.accepts("red"))
    }

    @Test
    fun `a stored name gives its colour, none gives a neutral icon`() {
        assertEquals(TagColor.TEAL, IconColor.of("TEAL"))
        assertNull(IconColor.of(null))
        assertNull(IconColor.of(""))
    }
}
