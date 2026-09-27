package com.assistant.core.ai.enrichments

import android.content.Context
import com.assistant.core.ai.data.EnrichmentType
import com.assistant.core.ai.data.MessageSegment
import com.assistant.core.ai.data.RichMessage
import com.assistant.core.ai.processing.FilterValues
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.ui.selectors.PointerDescription

/**
 * A zone or a tool as a pointer names it when it is read: its name, and a tool's type as shown.
 */
data class PointerPlace(val name: String, val typeName: String? = null)

/**
 * The text of a message's enrichments, written when the message is read: for the screen, and for
 * the AI at each send. Nothing of it is stored, so a pointer names its target as it is called now,
 * and a target deleted since reads as such.
 *
 * [load] reads every zone and every tool once, for all the blocks of the messages about to be
 * shown or sent. A target that is not among them was deleted; a read that fails is an error, never
 * taken for a deletion.
 */
class EnrichmentText private constructor(
    private val context: Context,
    private val zones: Map<String, PointerPlace>,
    private val tools: Map<String, PointerPlace>
) {
    private val s = Strings.`for`(context = context)

    /** What the user reads of [message]: its text, each block in brackets. */
    fun display(message: RichMessage): String = message.segments.joinToString("\n") { segment ->
        when (segment) {
            is MessageSegment.Text -> segment.content
            is MessageSegment.EnrichmentBlock -> "[${display(segment)}]"
        }
    }.trim()

    /** What the AI reads of [message]: its text, each block in brackets with its ids. */
    suspend fun prompt(message: RichMessage): String = message.segments.map { segment ->
        when (segment) {
            is MessageSegment.Text -> segment.content
            is MessageSegment.EnrichmentBlock -> "[${prompt(segment)}]"
        }
    }.joinToString("\n").trim()

    /** A block's text for the screen. */
    fun display(block: MessageSegment.EnrichmentBlock): String = when (block.type) {
        EnrichmentType.POINTER -> PointerConfig.fromJson(block.config).let { PointerDescription.block(it, place(it), s) }
        else -> EnrichmentProcessor(context).generateSummary(block.type, block.config)
    }

    /**
     * A block's text for the AI. A mention of narrowed entries carries how to read them, a
     * tool's values written by the types of its fields.
     */
    suspend fun prompt(block: MessageSegment.EnrichmentBlock): String = when (block.type) {
        EnrichmentType.POINTER -> {
            val pointer = PointerConfig.fromJson(block.config)
            val place = place(pointer)
            val fields: Map<String, FieldDefinition> =
                if (place != null && pointer.target.kind == PointerKind.TOOL && pointer.isMention && pointer.filters.length() > 0)
                    FilterValues.filterableFields(pointer.target.id!!, context, s)
                else emptyMap()
            PointerDescription.prompt(pointer, place, fields, s)
        }
        else -> EnrichmentProcessor(context).generateSummary(block.type, block.config)
    }

    /** The pointer's target as it is now, null when it was deleted. */
    private fun place(pointer: PointerConfig): PointerPlace? = when (pointer.target.kind) {
        PointerKind.ZONE -> zones[pointer.target.id]
        PointerKind.TOOL -> tools[pointer.target.id]
        else -> throw IllegalArgumentException("a pointer to ${pointer.target.kind} has no text yet")
    }

    companion object {
        /** Reads the zones and the tools the blocks may name. */
        suspend fun load(context: Context): EnrichmentText {
            val coordinator = Coordinator(context)

            val zonesResult = coordinator.processUserAction("zones.list", emptyMap())
            if (!zonesResult.isSuccess) throw IllegalStateException("Zones not read for the pointers' names: ${zonesResult.error}")
            val zones = (zonesResult.data?.get("zones") as? List<*> ?: emptyList<Any>())
                .filterIsInstance<Map<*, *>>()
                .associate { it["id"] as String to PointerPlace(it["name"] as? String ?: "") }

            val toolsResult = coordinator.processUserAction("tools.list_all", emptyMap())
            if (!toolsResult.isSuccess) throw IllegalStateException("Tools not read for the pointers' names: ${toolsResult.error}")
            val tools = (toolsResult.data?.get("tool_instances") as? List<*> ?: emptyList<Any>())
                .filterIsInstance<Map<*, *>>()
                .associate { tool ->
                    val typeName = (tool["tooltype"] as? String)?.let { ToolTypeManager.getToolType(it)?.getDisplayName(context) ?: it }
                    tool["id"] as String to PointerPlace(tool["name"] as? String ?: "", typeName)
                }

            return EnrichmentText(context, zones, tools)
        }
    }
}
