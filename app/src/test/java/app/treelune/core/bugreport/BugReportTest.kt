package app.treelune.core.bugreport

import app.treelune.core.database.entities.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * What the bug report promises (docs/design/bug-report.md): identifiers and quoted texts
 * replaced, the user's free text left as written, only recent WARN and ERROR lines, and a size
 * the share menu accepts.
 */
class BugReportTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val now = 1_760_000_000_000L
    private val device = BugReport.Device("0.5.0", 27, "Xiaomi", "Redmi Note 13", "15", 35, "en-US", "DefaultTheme")
    private val idA = "3f2b8c1e-4d5a-4b6c-8e9f-0a1b2c3d4e5f"
    private val idB = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    private fun line(level: String, ago: Long, message: String, throwable: String? = null) =
        LogEntry(timestamp = now - ago, level = level, tag = "AI", message = message, throwableMessage = throwable)

    @Test
    fun `an identifier becomes its number, the same everywhere in the report`() {
        val cleaner = ReportCleaner()
        assertEquals("tool #1 not found", cleaner.clean("tool $idA not found"))
        assertEquals("#2 then #1", cleaner.clean("$idB then ${idA.uppercase()}"))
    }

    @Test
    fun `a quoted text becomes a mark, a lone apostrophe stays`() {
        val cleaner = ReportCleaner()
        assertEquals("Field validation failed for ${ReportCleaner.QUOTED_MARK}", cleaner.clean("Field validation failed for 'Poids du matin'"))
        assertEquals("value ${ReportCleaner.QUOTED_MARK} refused", cleaner.clean("value \"72 kg\" refused"))
        assertEquals("the tool's config", cleaner.clean("the tool's config"))
    }

    @Test
    fun `only recent WARN and ERROR lines are taken, oldest first`() {
        val logs = listOf(
            line("ERROR", 1_000, "newest"),
            line("DEBUG", 2_000, "debug"),
            line("INFO", 3_000, "info"),
            line("VERBOSE", 4_000, "verbose"),
            line("WARN", 60_000, "older"),
            line("ERROR", BugReport.WINDOW_MILLIS + 1, "too old")
        )
        assertEquals(listOf("older", "newest"), BugReport.selectLines(logs, now).map { it.message })
    }

    @Test
    fun `at most the newest lines are taken`() {
        val logs = (1..BugReport.MAX_LINES + 50).map { line("WARN", it * 1_000L, "line $it") }
        val selected = BugReport.selectLines(logs, now)
        assertEquals(BugReport.MAX_LINES, selected.size)
        assertEquals("line 1", selected.last().message)
    }

    @Test
    fun `the free text goes as written, the crash and the lines cleaned`() {
        val crash = CrashFile.Crash(now - 5_000, "0.5.0", 27, "IllegalStateException: tool $idA 'Notes'", seen = false)
        val text = BugReport.build("I said 'hello' to $idA", device, crash, listOf(line("ERROR", 1_000, "schema of $idA")), zone)
        assertTrue(text.contains("I said 'hello' to $idA"))
        assertTrue(text.contains("IllegalStateException: tool #1 ${ReportCleaner.QUOTED_MARK}"))
        assertTrue(text.contains("ERROR AI: schema of #1"))
        assertTrue(text.contains("Xiaomi Redmi Note 13"))
    }

    @Test
    fun `no crash and no free text, no section for them`() {
        val text = BugReport.build("  ", device, null, emptyList(), zone)
        assertFalse(text.contains("## Last crash"))
        assertFalse(text.contains("## What happened"))
    }

    @Test
    fun `a report too long leaves out its oldest lines and says how many`() {
        val big = "x".repeat(5_000)
        val lines = (1..100).map { line("ERROR", (101 - it) * 1_000L, "line $it $big") }
        val text = BugReport.build("", device, null, lines, zone)
        assertTrue(text.length <= BugReport.MAX_LENGTH)
        assertTrue(text.contains("line 100 "))
        assertFalse(text.contains("line 1 "))
        assertTrue(Regex("\\((\\d+) older lines left out").containsMatchIn(text))
    }
}
