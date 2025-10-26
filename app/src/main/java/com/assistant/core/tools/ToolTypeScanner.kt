package com.assistant.core.tools

import com.assistant.tools.tracking.TrackingToolType
import com.assistant.tools.notes.NotesToolType
import com.assistant.tools.journal.JournalToolType
import com.assistant.tools.messages.MessageToolType

/**
 * Simple registry that lists known tool types
 * TODO: Replace with annotation processor scanning later
 */
object ToolTypeScanner {

    fun scanForToolTypes(): Map<String, ToolTypeContract> {
        return mapOf(
            "tracking" to TrackingToolType,
            "notes" to NotesToolType,
            "journal" to JournalToolType,
            "messages" to MessageToolType
        )
    }
}