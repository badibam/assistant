package com.assistant.core.fields.migration

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assistant.core.strings.Strings
import com.assistant.core.ui.*

/**
 * Confirmation dialog for custom fields migration.
 *
 * This dialog displays the detected changes and their impact on existing data,
 * allowing the user to confirm or cancel the migration before it's executed.
 *
 * The dialog shows:
 * - Title explaining that changes will affect existing data
 * - Description of all changes and their consequences
 * - Cancel and Confirm buttons
 *
 * Architecture:
 * - Reusable UI component
 * - Used by tool config screens before saving configuration
 * - Only shown if MigrationPolicy.requiresMigration() returns true
 *
 * @param changes List of detected configuration changes
 * @param strategies Map of migration strategies for each change
 * @param context Android context for string access
 * @param onConfirm Callback when user confirms migration
 * @param onCancel Callback when user cancels migration
 */
@Composable
fun MigrationConfirmationDialog(
    changes: List<FieldChange>,
    strategies: Map<FieldChange, MigrationStrategy>,
    context: Context,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val s = Strings.`for`(context = context)

    // Generate description of migration actions
    val description = MigrationPolicy.getDescription(changes, strategies, context)

    UI.Dialog(
        type = DialogType.CONFIRM,
        onConfirm = onConfirm,
        onCancel = onCancel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            UI.Text(
                text = s.shared("migration_confirm_title"),
                type = TextType.TITLE,
                fillMaxWidth = true
            )

            // Main message
            UI.Text(
                text = s.shared("migration_confirm_message"),
                type = TextType.BODY,
                fillMaxWidth = true
            )

            // Detailed description of changes
            UI.Card(type = CardType.DEFAULT) {
                Box(modifier = Modifier.padding(16.dp)) {
                    UI.Text(
                        text = description,
                        type = TextType.BODY,
                        fillMaxWidth = true
                    )
                }
            }

            // Warning note
            UI.Text(
                text = s.shared("migration_warning_note"),
                type = TextType.CAPTION,
                fillMaxWidth = true
            )
        }
    }
}
