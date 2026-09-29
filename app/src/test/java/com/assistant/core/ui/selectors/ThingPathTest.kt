package com.assistant.core.ui.selectors

import com.assistant.core.selection.Reference
import com.assistant.core.selection.ReferenceKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the input of a thing stands: the deepest thing reached is what it designates. */
class ThingPathTest {

    private val zone = Named("z1", "Health")
    private val tool = Named("t1", "Food", "structured")
    private val entry = Named("e1", "Apple")
    private val variable = Named("v1", "kcal")

    @Test
    fun `the deepest thing reached is the one designated`() {
        assertEquals(Reference(ReferenceKind.APP, null), ThingPath().reference)
        assertEquals(Reference(ReferenceKind.ZONE, "z1"), ThingPath(zone).reference)
        assertEquals(Reference(ReferenceKind.TOOL_INSTANCE, "t1"), ThingPath(zone, tool).reference)
        assertEquals(Reference(ReferenceKind.ENTRY, "e1"), ThingPath(zone, tool, entry).reference)
    }

    @Test
    fun `going back up leaves what lies below`() {
        val deep = ThingPath(zone, tool, entry)
        assertEquals(ThingPath(zone, tool), deep.upTo(ReferenceKind.TOOL_INSTANCE))
        assertEquals(ThingPath(zone), deep.upTo(ReferenceKind.ZONE))
        assertEquals(ThingPath(), deep.upTo(ReferenceKind.APP))
    }

    @Test
    fun `the path survives a rotation whole`() {
        val deep = ThingPath(zone, tool, entry)
        assertEquals(deep, ThingPath.fromJson(deep.toJson()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an entry is only reached through its tool`() {
        ThingPath(zone, entry = entry)
    }

    @Test
    fun `a variable is reached in its zone, and designated there`() {
        val atVariable = ThingPath(zone, variable = variable)
        assertEquals(Reference(ReferenceKind.VARIABLE, "v1"), atVariable.reference)
        assertEquals(ThingPath(zone), atVariable.upTo(ReferenceKind.ZONE))
        assertEquals(atVariable, ThingPath.fromJson(atVariable.toJson()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a variable is not under a tool`() {
        ThingPath(zone, tool, variable = variable)
    }

    /** A browser at a tool leads to its entries alone; at a variable, to nothing. */
    @Test
    fun `what lies below each kind`() {
        assertEquals(setOf(ReferenceKind.TOOL_INSTANCE, ReferenceKind.VARIABLE, ReferenceKind.ENTRY), ReferenceKind.ZONE.below)
        assertEquals(setOf(ReferenceKind.ENTRY), ReferenceKind.TOOL_INSTANCE.below)
        assertEquals(emptySet<ReferenceKind>(), ReferenceKind.VARIABLE.below)
    }
}
