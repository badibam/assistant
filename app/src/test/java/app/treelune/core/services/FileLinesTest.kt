package app.treelune.core.services

import org.junit.Assert.assertEquals
import org.junit.Test

/** A joined file is read by lines, whatever ends them, never a line cut in two. */
class FileLinesTest {

    private val csv = "name;kcal\r\napple;52\r\npasta;350\r\n"

    @Test
    fun `lines are counted whatever ends them, a last break opening none`() {
        assertEquals(3, FileLines.count(csv))
        assertEquals(3, FileLines.count("a\nb\rc"))
        assertEquals(0, FileLines.count(""))
    }

    @Test
    fun `a part starts at its line and holds whole lines`() {
        assertEquals(FileLines.Part("apple;52\npasta;350", 2), FileLines.window(csv, 2, 2))
    }

    @Test
    fun `without a count, the rest of the file`() {
        assertEquals(FileLines.Part("name;kcal\napple;52\npasta;350", 3), FileLines.window(csv, 1, null))
    }

    @Test
    fun `past the end, what remains, or nothing`() {
        assertEquals(FileLines.Part("pasta;350", 1), FileLines.window(csv, 3, 10))
        assertEquals(FileLines.Part("", 0), FileLines.window(csv, 9, 2))
    }
}
