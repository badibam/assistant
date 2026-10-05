package com.assistant.core.ai.ui.components

import com.assistant.core.ai.data.EnrichmentType
import com.assistant.core.ai.data.MessageSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the composer's blocks (RichComposer): a message is a list of typed blocks, a new one goes
 * after the active block, the list is never empty, and an empty text does not go with the message.
 */
class ComposerBlocksTest {

    private fun text(content: String, id: String) = ComposerBlock(MessageSegment.Text(content), id)
    private fun pointer(id: String) = ComposerBlock(MessageSegment.EnrichmentBlock(EnrichmentType.POINTER, "{}"), id)
    private fun ids(blocks: List<ComposerBlock>) = blocks.map { it.id }

    @Test
    fun `a message is one block per segment, in order`() {
        val segments = listOf(
            MessageSegment.Text("compare"),
            MessageSegment.EnrichmentBlock(EnrichmentType.POINTER, "{}"),
            MessageSegment.EnrichmentBlock(EnrichmentType.POINTER, "{\"x\":1}"),
            MessageSegment.Text("please")
        )
        val blocks = ComposerBlocks.fromSegments(segments)
        assertEquals(segments, blocks.map { it.segment })
        assertEquals(segments, ComposerBlocks.toSegments(blocks))
    }

    @Test
    fun `no segment gives a single empty text`() {
        val blocks = ComposerBlocks.fromSegments(emptyList())
        assertEquals(listOf<MessageSegment>(MessageSegment.Text("")), blocks.map { it.segment })
    }

    @Test
    fun `an empty text does not go with the message`() {
        val blocks = listOf(text("", "a"), pointer("b"), text("", "c"), text("ok", "d"))
        assertEquals(listOf(pointer("b").segment, MessageSegment.Text("ok")), ComposerBlocks.toSegments(blocks))
    }

    @Test
    fun `a block goes right after the active one`() {
        val blocks = listOf(text("1", "a"), text("2", "b"), text("3", "c"))
        assertEquals(listOf("a", "b", "new", "c"), ids(ComposerBlocks.insertAfter(blocks, "b", pointer("new"))))
        assertEquals(listOf("a", "b", "c", "new"), ids(ComposerBlocks.insertAfter(blocks, "c", pointer("new"))))
    }

    @Test(expected = IllegalStateException::class)
    fun `a block aimed after one that is gone is refused`() {
        ComposerBlocks.insertAfter(listOf(text("1", "a")), "gone", pointer("new"))
    }

    @Test
    fun `an edited block keeps its place`() {
        val blocks = listOf(text("1", "a"), pointer("b"), text("3", "c"))
        val edited = MessageSegment.EnrichmentBlock(EnrichmentType.POINTER, "{\"y\":2}")
        val result = ComposerBlocks.replace(blocks, "b", edited)
        assertEquals(listOf("a", "b", "c"), ids(result))
        assertEquals(edited, result[1].segment)
    }

    @Test
    fun `a moved block keeps its id`() {
        val blocks = listOf(text("1", "a"), pointer("b"), text("3", "c"))
        assertEquals(listOf("b", "c", "a"), ids(ComposerBlocks.move(blocks, 0, 2)))
        assertEquals(listOf("c", "a", "b"), ids(ComposerBlocks.move(blocks, 2, 0)))
    }

    @Test
    fun `deleting another block leaves the active one active`() {
        val blocks = listOf(text("1", "a"), pointer("b"), text("3", "c"))
        val deletion = ComposerBlocks.delete(blocks, "a", activeId = "c")
        assertEquals(listOf("b", "c"), ids(deletion.blocks))
        assertEquals("c", deletion.activeId)
    }

    @Test
    fun `deleting the active block activates the one before it, or after it when it was first`() {
        val blocks = listOf(text("1", "a"), pointer("b"), text("3", "c"))
        assertEquals("a", ComposerBlocks.delete(blocks, "b", activeId = "b").activeId)
        assertEquals("b", ComposerBlocks.delete(blocks, "a", activeId = "a").activeId)
    }

    @Test
    fun `the last block deleted leaves an empty text, active`() {
        val deletion = ComposerBlocks.delete(listOf(pointer("a")), "a", activeId = "a")
        assertEquals(1, deletion.blocks.size)
        assertTrue(deletion.blocks.single().isEmptyText)
        assertEquals(deletion.blocks.single().id, deletion.activeId)
    }

    @Test
    fun `a message of pointers alone keeps no text`() {
        val blocks = listOf(pointer("a"), pointer("b"))
        val deletion = ComposerBlocks.delete(blocks, "a", activeId = "a")
        assertEquals(listOf("b"), ids(deletion.blocks))
    }
}
