package app.treelune.core.ui.variables

import org.junit.Assert.assertEquals
import org.junit.Test

/** The formula editor's buttons insert where the cursor stands, set apart by spaces. */
class InsertTokenTest {

    @Test
    fun `a name goes where the cursor stands, not at the end`() {
        // "kcal + |" then "× 2" typed after: the cursor is between "+ " and "× 2"
        assertEquals("kcal + portion × 2" to 14, insertToken("kcal + × 2", 7, 7, "portion"))
    }

    @Test
    fun `an empty formula takes the token alone`() {
        assertEquals("kcal" to 4, insertToken("", 0, 0, "kcal"))
    }

    @Test
    fun `a selection is replaced by the token`() {
        assertEquals("a × b" to 3, insertToken("a + b", 2, 3, "×"))
    }

    @Test
    fun `nothing is spaced inside parentheses nor after a function's opening one`() {
        assertEquals("hours(" to 6, insertToken("", 0, 0, "hours("))
        assertEquals("hours(sleep)" to 11, insertToken("hours()", 6, 6, "sleep"))
    }
}
