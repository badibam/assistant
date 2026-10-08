package app.treelune.core.validation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the grammar of the field paths a TOOL_DATA query names. The validation that answers
 * the AI and the filter that builds the result both read paths through it, so a path it
 * accepts is one the filter can serve, and one it cannot serve is refused out loud.
 */
class FieldPatternGrammarTest {

    /** The four shapes, sorted by where they point, names inside a container without their prefix. */
    @Test
    fun theFourShapes_areSortedByWhereTheyPoint() {
        val parsed = FieldPatternGrammar.parse(listOf("timestamp", "name", "data.quantity", "data.unit", "extra.mood", "state.running"))

        assertEquals(listOf("timestamp", "name"), parsed.root)
        assertEquals(listOf("quantity", "unit"), parsed.inside["data"])
        assertEquals(listOf("mood"), parsed.inside["extra"])
        assertEquals(listOf("running"), parsed.inside["state"])
        assertEquals(emptyList<String>(), parsed.invalid)
    }

    /** A container on its own, or a prefix with no name after it, asks for no field. */
    @Test
    fun containersAndEmptyNames_areRefused() {
        val paths = listOf("data", "extra", "state", "data.", "extra.", "state.", "", "  ")

        assertEquals(paths, FieldPatternGrammar.parse(paths).invalid)
    }

    /** A dotted path under anything but data, extra or state points nowhere. */
    @Test
    fun anotherDottedPath_isRefused() {
        val paths = listOf("config.name", "Data.quantity", "custom.mood")

        assertEquals(paths, FieldPatternGrammar.parse(paths).invalid)
    }

    /**
     * One level deeper is refused: the filter keeps whole keys and would look for a key named
     * "sleep.start", find none and drop the field without a word.
     */
    @Test
    fun aPathOneLevelTooDeep_isRefused() {
        val parsed = FieldPatternGrammar.parse(listOf("extra.sleep.start", "data.quantity.value", "extra.sleep"))

        assertEquals(listOf("extra.sleep.start", "data.quantity.value"), parsed.invalid)
        assertEquals(listOf("sleep"), parsed.inside["extra"])
        assertEquals(null, parsed.inside["data"])
    }
}
