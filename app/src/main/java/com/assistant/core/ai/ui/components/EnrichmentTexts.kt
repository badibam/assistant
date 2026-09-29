package com.assistant.core.ai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.ai.data.MessageSegment
import com.assistant.core.ai.data.RichMessage
import com.assistant.core.ai.enrichments.EnrichmentText
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager

/**
 * [message] as the user reads it, its blocks naming their targets as they are now
 * (EnrichmentText): read again when a zone or a tool changes, a rename showing at once. Each
 * block reads "…" while the names are read, and says so if they cannot be.
 */
@Composable
fun rememberDisplayText(message: RichMessage): String {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    fun withBlocks(text: String) = message.segments.joinToString("\n") { segment ->
        when (segment) {
            is MessageSegment.Text -> segment.content
            is MessageSegment.EnrichmentBlock -> "[$text]"
        }
    }.trim()
    val text by produceState(initialValue = withBlocks("…"), message) {
        whileNamesHold {
            value = try {
                EnrichmentText.load(context, EnrichmentText.blocksOf(listOf(message))).display(message)
            } catch (e: Exception) {
                LogManager.aiUI("Enrichment texts not read: ${e.message}", "ERROR", e)
                withBlocks(s.shared("ai_enrichment_unreadable"))
            }
        }
    }
    return text
}

/** One block's text for the screen, as [rememberDisplayText] writes it, without brackets. */
@Composable
fun rememberDisplayText(block: MessageSegment.EnrichmentBlock): String {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val text by produceState(initialValue = "…", block) {
        whileNamesHold {
            value = try {
                EnrichmentText.load(context, listOf(block)).display(block)
            } catch (e: Exception) {
                LogManager.aiUI("Enrichment text not read: ${e.message}", "ERROR", e)
                s.shared("ai_enrichment_unreadable")
            }
        }
    }
    return text
}

/** Runs [read] now, then again each time a zone or a tool changes: what a block may name. */
private suspend fun whileNamesHold(read: suspend () -> Unit) {
    read()
    com.assistant.core.utils.DataChangeNotifier.changes.collect { change ->
        if (change is com.assistant.core.utils.DataChangeEvent.ZonesChanged || change is com.assistant.core.utils.DataChangeEvent.ToolsChanged) read()
    }
}
