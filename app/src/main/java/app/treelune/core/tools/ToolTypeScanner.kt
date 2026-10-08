package app.treelune.core.tools

import app.treelune.tools.tracking.TrackingToolType
import app.treelune.tools.notes.NotesToolType
import app.treelune.tools.journal.JournalToolType
import app.treelune.tools.messages.MessageToolType
import app.treelune.tools.list.ListToolType
import app.treelune.tools.structured.StructuredToolType
import app.treelune.tools.goal.GoalToolType
import app.treelune.tools.questionnaire.QuestionnaireToolType
import app.treelune.tools.chart.ChartToolType
import app.treelune.tools.sequence.SequenceToolType

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
            "messages" to MessageToolType,
            "list" to ListToolType,
            "structured" to StructuredToolType,
            "goal" to GoalToolType,
            "questionnaire" to QuestionnaireToolType,
            "chart" to ChartToolType,
            "sequence" to SequenceToolType
        )
    }
}