package com.assistant.core.fields.migration

import android.content.Context
import androidx.compose.runtime.*
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.launch

/**
 * Reusable composable handler for custom fields migration in tool config screens.
 *
 * This composable encapsulates the complete migration workflow:
 * 1. Checks if migration is needed when user tries to save
 * 2. Shows confirmation dialog if changes require data migration
 * 3. Executes migration after user confirmation
 * 4. Calls success callback for config save
 *
 * Usage in ConfigScreens:
 * ```kotlin
 * val migrationHandler = rememberCustomFieldsMigrationHandler(
 *     toolInstanceId = existingToolId,
 *     oldFields = oldCustomFields,
 *     newFields = customFields,
 *     context = context,
 *     onSuccess = { /* save config here */ },
 *     onError = { error -> errorMessage = error }
 * )
 *
 * // In your save handler:
 * migrationHandler.checkAndProceed()
 * ```
 *
 * @param toolInstanceId Tool instance ID (only needed when editing)
 * @param oldFields Original custom fields configuration
 * @param newFields New custom fields configuration
 * @param context Android context
 * @param onSuccess Callback when migration succeeds or is not needed
 * @param onError Callback when migration fails or is blocked
 * @return MigrationHandler with checkAndProceed() function
 */
@Composable
fun rememberCustomFieldsMigrationHandler(
    toolInstanceId: String?,
    oldFields: List<FieldDefinition>,
    newFields: List<FieldDefinition>,
    context: Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
): CustomFieldsMigrationHandler {
    val s = Strings.`for`(context = context)
    val coordinator = remember { Coordinator(context) }
    val coroutineScope = rememberCoroutineScope()

    // Migration dialog state
    var showMigrationDialog by remember { mutableStateOf(false) }
    var pendingMigration by remember { mutableStateOf<MigrationCheckResult.NeedsMigration?>(null) }
    var isMigrating by remember { mutableStateOf(false) }

    // Handler instance
    val handler = remember {
        CustomFieldsMigrationHandler(
            checkAndProceed = {
                coroutineScope.launch {
                    // Only check migration if editing an existing tool
                    if (toolInstanceId != null) {
                        LogManager.ui("DEBUG Migration Handler: toolInstanceId=$toolInstanceId")
                        LogManager.ui("DEBUG Migration Handler: oldFields=${oldFields.map { it.name }}")
                        LogManager.ui("DEBUG Migration Handler: newFields=${newFields.map { it.name }}")

                        val migrationCheck = FieldMigrationHelper.checkMigrationNeeded(
                            oldFields = oldFields,
                            newFields = newFields,
                            context = context
                        )

                        LogManager.ui("DEBUG Migration Handler: migrationCheck type=${migrationCheck::class.simpleName}")

                        when (migrationCheck) {
                            is MigrationCheckResult.Error -> {
                                // Migration blocked - show error
                                LogManager.ui("Migration blocked: ${migrationCheck.errorMessage}", "ERROR")
                                onError(migrationCheck.errorMessage)
                            }

                            is MigrationCheckResult.NeedsMigration -> {
                                // Migration needed - show dialog
                                LogManager.ui("Migration needed - showing confirmation dialog")
                                pendingMigration = migrationCheck
                                showMigrationDialog = true
                            }

                            MigrationCheckResult.NoMigration -> {
                                // No migration needed - proceed directly
                                LogManager.ui("No migration needed - proceeding to save")
                                onSuccess()
                            }
                        }
                    } else {
                        // Creating new tool - no migration needed
                        onSuccess()
                    }
                }
            }
        )
    }

    // Migration confirmation dialog
    if (showMigrationDialog && pendingMigration != null && toolInstanceId != null) {
        MigrationConfirmationDialog(
            changes = pendingMigration!!.changes,
            strategies = pendingMigration!!.strategies,
            context = context,
            onConfirm = {
                showMigrationDialog = false
                pendingMigration = null
                LogManager.ui("User confirmed migration - proceeding to save (Service will execute migration)")

                // User confirmed - proceed to save
                // The Service (ToolInstanceService) will execute the migration automatically
                onSuccess()
            },
            onCancel = {
                showMigrationDialog = false
                pendingMigration = null
                LogManager.ui("Migration cancelled by user")
            }
        )
    }

    return handler
}

/**
 * Handler instance with checkAndProceed function.
 */
data class CustomFieldsMigrationHandler(
    val checkAndProceed: () -> Unit
)
