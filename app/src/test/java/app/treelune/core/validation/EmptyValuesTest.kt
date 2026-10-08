package app.treelune.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers what the validator leaves out before checking: a null, and a list whose every element was
 * null, but not a list sent empty. Dropped, an empty list required by its schema read as missing:
 * the home screen's last zone group could not be removed, and the demo's reinstall, which removes
 * its group, failed after deleting the demo.
 */
class EmptyValuesTest {

    @Test
    fun `a list sent empty is kept`() {
        assertEquals(mapOf("zone_groups" to emptyList<Any>()), SchemaValidator.filterEmptyValues(mapOf("zone_groups" to emptyList<Any>())))
    }

    @Test
    fun `a null and a list of nulls only are left out`() {
        val filtered = SchemaValidator.filterEmptyValues(mapOf("a" to null, "b" to listOf(null, null), "c" to listOf(null, "x")))
        assertFalse("a" in filtered)
        assertFalse("b" in filtered)
        assertEquals(listOf("x"), filtered["c"])
    }
}
