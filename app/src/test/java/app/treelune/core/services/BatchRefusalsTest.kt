package app.treelune.core.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRefusalsTest {

    private val strings = mapOf(
        "batch_refused_all" to "All %1\$s refused: %2\$s",
        "batch_refusal_reason" to "%1\$s (%2\$s, entries %3\$s)",
        "batch_refusal_more" to "; and %1\$s other reasons"
    )
    private val text: (String) -> String = { strings.getValue(it) }

    @Test
    fun `refusals for one reason are one line, however many`() {
        val summary = BatchRefusals.summary((0 until 64_660).map { it to "name missing" }, text)
        assertEquals("All 64660 refused: name missing (64660, entries 0, 1, 2, 3, 4…)", summary)
    }

    @Test
    fun `reasons are said most frequent first, the rest counted`() {
        val refusals = (0 until 7).flatMap { reason -> (0..reason).map { (reason * 10 + it) to "reason $reason" } }
        val summary = BatchRefusals.summary(refusals, text)
        assertTrue(summary.startsWith("All 28 refused: reason 6 (7, entries 60, 61, 62, 63, 64…); reason 5"))
        assertTrue(summary.endsWith("; and 2 other reasons"))
    }
}
