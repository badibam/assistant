package com.assistant.tools.journal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assistant.core.ui.*
import com.assistant.core.strings.Strings
import com.assistant.tools.journal.utils.DateFormatUtils

/**
 * Card component for displaying journal entries in list
 *
 * Displays:
 * - Date/time (formatted with formatJournalDate)
 * - Title (entry name)
 * - Content preview (first 150 characters)
 * - The user's fields, compact (CustomFieldsDisplay)
 *
 * @param entryId Entry ID
 * @param timestamp Entry timestamp
 * @param title Entry title (name field)
 * @param content Entry content text
 * @param config The tool's config, for how its fields show
 * @param extra The entry's values of the user's fields
 * @param onClick Callback when card is clicked
 */
@Composable
fun JournalCard(
    entryId: String,
    timestamp: Long,
    title: String,
    content: String,
    config: org.json.JSONObject,
    extra: Map<String, Any?>,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(tool = "journal", context = context) }

    // Format date using utility function
    val formattedDate = remember(timestamp) {
        DateFormatUtils.formatJournalDate(timestamp, context)
    }

    // Prepare content preview (max 150 chars)
    val contentPreview = remember(content) {
        if (content.length <= 150) {
            content
        } else {
            content.take(150) + "..."
        }
    }

    // Display title or placeholder
    val displayTitle = remember(title) {
        title.ifBlank { s.tool("placeholder_untitled") }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Date/time line
        UI.Text(
            text = formattedDate,
            type = TextType.CAPTION
        )

        // Title line
        UI.Text(
            text = displayTitle,
            type = TextType.SUBTITLE
        )

        // Content preview (if not just placeholder)
        if (content != "...") {
            UI.Text(
                text = contentPreview,
                type = TextType.BODY
            )
        }

        // The user's fields, compact: a summary, where they are read at a glance
        com.assistant.core.fields.CustomFieldsDisplay(
            toolType = com.assistant.tools.journal.JournalToolType,
            config = config,
            values = extra,
            layout = com.assistant.core.fields.FieldsLayout.COMPACT,
            context = context
        )
    }
}
