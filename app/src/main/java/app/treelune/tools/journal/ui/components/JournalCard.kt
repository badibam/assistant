package app.treelune.tools.journal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ui.*
import app.treelune.core.strings.Strings
import app.treelune.tools.journal.utils.DateFormatUtils

/**
 * Card component for displaying journal entries in list
 *
 * Displays:
 * - Date/time (formatted with formatJournalDate)
 * - Title (entry name)
 * - Content preview (first 150 characters)
 * - The user's fields, one per line (CustomFieldsDisplay), parted from the text by a line
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
            .padding(UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.XS)
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

        // The user's fields, one per line under a line parting them from the text: a summary,
        // where they are read at a glance
        if (app.treelune.core.fields.shownCustomFields(config, extra).isNotEmpty()) {
            UI.Divider()
            app.treelune.core.fields.CustomFieldsDisplay(
                toolType = app.treelune.tools.journal.JournalToolType,
                config = config,
                values = extra,
                layout = app.treelune.core.fields.FieldsLayout.LINE,
                context = context
            )
        }
    }
}
