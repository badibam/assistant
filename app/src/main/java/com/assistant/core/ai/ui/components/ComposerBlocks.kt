package com.assistant.core.ai.ui.components

import com.assistant.core.ai.data.MessageSegment
import java.util.UUID

/**
 * One block of the message being composed: a segment of the message (a text, a pointer, a file),
 * with the id the composer edits it by — which block is active, which one a dialog edits.
 * The id is the composer's alone: the message keeps only the segment.
 */
data class ComposerBlock(
    val segment: MessageSegment,
    val id: String = UUID.randomUUID().toString()
) {
    /** Whether the block is a text with nothing written: it does not go with the message. */
    val isEmptyText: Boolean get() = segment is MessageSegment.Text && segment.content.isEmpty()
}

/**
 * The composer's blocks and what can be done to them, kept out of the composable so it can be
 * tested. The list is never empty: there is always a block to write in and one to insert after.
 */
object ComposerBlocks {

    /** A single empty text: the composer at the start, after a send, once its last block is deleted. */
    fun empty(): List<ComposerBlock> = listOf(ComposerBlock(MessageSegment.Text("")))

    /** The blocks of [segments], one per segment, in their order; an empty text when there are none. */
    fun fromSegments(segments: List<MessageSegment>): List<ComposerBlock> =
        if (segments.isEmpty()) empty() else segments.map { ComposerBlock(it) }

    /** The message the blocks make: their segments in order, the empty texts left out. */
    fun toSegments(blocks: List<ComposerBlock>): List<MessageSegment> =
        blocks.filterNot { it.isEmptyText }.map { it.segment }

    /**
     * [block] put right after the block [afterId], which must be there: a block aimed after one
     * that is gone would land nowhere the user chose.
     */
    fun insertAfter(blocks: List<ComposerBlock>, afterId: String, block: ComposerBlock): List<ComposerBlock> {
        val index = indexOf(blocks, afterId)
        return blocks.toMutableList().apply { add(index + 1, block) }
    }

    /** The block [id] given [segment] in its place. */
    fun replace(blocks: List<ComposerBlock>, id: String, segment: MessageSegment): List<ComposerBlock> {
        indexOf(blocks, id)
        return blocks.map { if (it.id == id) it.copy(segment = segment) else it }
    }

    /** The block at [from] put at [to], as the reorderable column gives a move. */
    fun move(blocks: List<ComposerBlock>, from: Int, to: Int): List<ComposerBlock> =
        blocks.toMutableList().apply { add(to, removeAt(from)) }

    /** The blocks once [id] is deleted, and the one active then. */
    data class Deletion(val blocks: List<ComposerBlock>, val activeId: String)

    /**
     * The block [id] deleted. The active block stays active, unless it is the one deleted: then
     * the one before it is, or the one after when it was the first. The last block left is
     * replaced by an empty text.
     */
    fun delete(blocks: List<ComposerBlock>, id: String, activeId: String): Deletion {
        val index = indexOf(blocks, id)
        val left = blocks.filter { it.id != id }
        if (left.isEmpty()) return empty().let { Deletion(it, it.first().id) }
        val newActive = when {
            id != activeId -> activeId
            index > 0 -> left[index - 1].id
            else -> left.first().id
        }
        return Deletion(left, newActive)
    }

    private fun indexOf(blocks: List<ComposerBlock>, id: String): Int =
        blocks.indexOfFirst { it.id == id }.also { check(it >= 0) { "Block $id is not in the composer" } }
}
