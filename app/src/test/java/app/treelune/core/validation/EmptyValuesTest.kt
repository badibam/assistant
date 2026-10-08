package app.treelune.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers what the validator leaves out of its copy before checking: a key set to null, and
 * nothing of a list. The data stored is the one sent, so a null left out of a list would be stored
 * unchecked; and an empty list dropped read as missing: the home screen's last zone group could
 * not be removed, and the demo's reinstall, which removes its group, failed after deleting the demo.
 */
class EmptyValuesTest {

    @Test
    fun `a list sent empty is kept`() {
        assertEquals(mapOf("zone_groups" to emptyList<Any>()), SchemaValidator.filterEmptyValues(mapOf("zone_groups" to emptyList<Any>())))
    }

    @Test
    fun `a list is checked as sent, its nulls included`() {
        val filtered = SchemaValidator.filterEmptyValues(mapOf("b" to listOf(null, null), "c" to listOf(null, "x")))
        assertEquals(listOf(null, null), filtered["b"])
        assertEquals(listOf(null, "x"), filtered["c"])
    }

    @Test
    fun `a key set to null is left out, in an object inside a list too`() {
        val filtered = SchemaValidator.filterEmptyValues(mapOf("a" to null, "rows" to listOf(mapOf("k" to null, "v" to 1))))
        assertFalse("a" in filtered)
        assertEquals(listOf(mapOf("v" to 1)), filtered["rows"])
    }
}
