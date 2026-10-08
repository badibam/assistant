package app.treelune.core.ai.enrichments

import org.junit.Assert.assertEquals
import org.junit.Test

/** A joined file goes with its message whole, or its first lines only; its id always goes. */
class FileEnrichmentTest {

    @Test
    fun `whole, the file is read without a range`() {
        val query = FileEnrichment("f1", "aliments.csv", 412).query(isRelative = false)
        assertEquals("FILE", query.type)
        assertEquals(mapOf("id" to "f1"), query.params)
    }

    @Test
    fun `previewed, only its first lines are read`() {
        val query = FileEnrichment("f1", "aliments.csv", 412, whole = false).query(isRelative = false)
        assertEquals(mapOf("id" to "f1", "start_line" to 1, "lines" to FileEnrichment.PREVIEW_LINES), query.params)
    }

    @Test
    fun `the stored form reads back whole`() {
        val file = FileEnrichment("f1", "aliments.csv", 412, whole = false)
        assertEquals(file, FileEnrichment.fromJson(file.toJson().toString()))
    }
}
