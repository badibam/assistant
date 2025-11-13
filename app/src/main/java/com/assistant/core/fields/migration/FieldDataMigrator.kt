package com.assistant.core.fields.migration

import android.content.Context
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * Executes data migration after custom field configuration changes.
 *
 * This migrator applies migration strategies to existing tool_data entries,
 * modifying custom_fields maps according to the detected configuration changes.
 *
 * Migration flow:
 * 1. Load all tool_data entries for the tool instance
 * 2. For each entry, transform custom_fields map according to strategies
 * 3. Batch update modified entries via CommandDispatcher
 * 4. Return result with success/failure status
 *
 * Supported transformations:
 * - STRIP_FIELD: Remove field from all entries
 * - STRIP_FIELD_IF_VALUE: Remove field if value matches condition
 *
 * Architecture:
 * - Uses CommandDispatcher exclusively (respects discovery pattern)
 * - No direct database access
 * - Cancellable via CancellationToken
 * - V1: No pagination, no rollback on failure
 *
 * Usage:
 * - UI: After user confirms migration in dialog
 * - AI: Silently before saving config (no user prompt)
 */
object FieldDataMigrator {

    /**
     * Migrate custom_fields data for a tool instance.
     *
     * This function loads all entries for the tool instance, applies the migration
     * strategies to transform custom_fields maps, and batch updates the database.
     *
     * @param coordinator CommandDispatcher coordinator for data access
     * @param toolInstanceId Tool instance to migrate
     * @param changes List of detected configuration changes
     * @param strategies Map of migration strategies for each change
     * @param context Android context for string access
     * @param token Cancellation token for abort support
     * @return OperationResult with success status and error message if failed
     */
    suspend fun migrateCustomFields(
        coordinator: Coordinator,
        toolInstanceId: String,
        changes: List<FieldChange>,
        strategies: Map<FieldChange, MigrationStrategy>,
        context: Context,
        token: CancellationToken
    ): OperationResult {
        val s = Strings.`for`(context = context)

        try {
            LogManager.service(
                "Starting custom fields migration for tool instance: $toolInstanceId",
                "INFO"
            )

            // Check if migration is actually needed
            if (!MigrationPolicy.requiresMigration(strategies)) {
                LogManager.service(
                    "No data migration required (strategies: ${strategies.values})",
                    "INFO"
                )
                return OperationResult.success()
            }

            // Check cancellation before starting
            if (token.isCancelled) {
                LogManager.service("Migration cancelled before start", "INFO")
                return OperationResult.error(s.shared("error_operation_cancelled"))
            }

            // Step 1: Load all entries for this tool instance
            val loadResult = coordinator.processUserAction(
                "tool_data.get",
                mapOf(
                    "toolInstanceId" to toolInstanceId
                    // No pagination in V1 - load all entries
                )
            )

            if (loadResult.status != CommandStatus.SUCCESS) {
                val error = loadResult.error ?: s.shared("error_load_data_failed")
                LogManager.service("Failed to load tool data: $error", "ERROR")
                return OperationResult.error(error)
            }

            // Extract entries array from result
            @Suppress("UNCHECKED_CAST")
            val entries = (loadResult.data?.get("entries") as? List<Map<String, Any>>)
                ?: emptyList()

            LogManager.service(
                "Loaded ${entries.size} entries for migration",
                "INFO"
            )

            // Check cancellation after load
            if (token.isCancelled) {
                LogManager.service("Migration cancelled after load", "INFO")
                return OperationResult.error(s.shared("error_operation_cancelled"))
            }

            // Step 2: Transform each entry's custom_fields map
            val entriesToUpdate = mutableListOf<Map<String, Any>>()
            var modifiedCount = 0

            entries.forEach { entry ->
                // Check cancellation periodically
                if (token.isCancelled) {
                    LogManager.service("Migration cancelled during transformation", "INFO")
                    return OperationResult.error(s.shared("error_operation_cancelled"))
                }

                // Extract entry ID and custom_fields
                val entryId = entry["id"] as? String
                if (entryId == null) {
                    LogManager.service("Entry missing ID, skipping", "WARN")
                    return@forEach
                }

                // Extract custom_fields (can be String JSON, Map, or null)
                val customFieldsRaw = entry["custom_fields"]
                LogManager.service(
                    "DEBUG Entry $entryId: custom_fields type=${customFieldsRaw?.javaClass?.simpleName}, value=$customFieldsRaw",
                    "DEBUG"
                )

                // Parse custom_fields using JsonUtils (handles String JSON, JSONObject, Map, or null)
                val customFields = try {
                    JsonUtils.toMap(customFieldsRaw)
                } catch (e: Exception) {
                    LogManager.service("Entry $entryId: Error parsing custom_fields: ${e.message}", "ERROR", e)
                    null
                }

                // Skip if no custom fields
                if (customFields.isNullOrEmpty()) {
                    LogManager.service("Entry $entryId: No custom fields or empty, skipping", "DEBUG")
                    return@forEach
                }

                LogManager.service("Entry $entryId: Processing ${customFields.size} custom fields: ${customFields.keys}", "DEBUG")

                // Apply migration strategies to transform custom_fields
                val transformed = applyMigrationStrategies(
                    customFields = customFields,
                    changes = changes,
                    strategies = strategies
                )

                // Check if anything changed
                if (transformed != customFields) {
                    modifiedCount++

                    // Build update with explicit null for removed fields
                    // Since batch_update merges custom_fields (preserves absent fields),
                    // we must explicitly send null for fields that were removed
                    val updateMap = transformed.toMutableMap()

                    // Add null for fields that were removed
                    customFields.keys.forEach { oldKey ->
                        if (oldKey !in transformed) {
                            updateMap[oldKey] = JSONObject.NULL
                        }
                    }

                    // Convert Map to JSONObject for service compatibility
                    entriesToUpdate.add(
                        mapOf(
                            "id" to entryId,
                            "custom_fields" to JsonUtils.toJSONObject(updateMap)
                        )
                    )
                }
            }

            LogManager.service(
                "Transformed $modifiedCount / ${entries.size} entries",
                "INFO"
            )

            // Step 3: Batch update modified entries
            if (entriesToUpdate.isEmpty()) {
                LogManager.service("No entries needed modification", "INFO")
                return OperationResult.success()
            }

            // Check cancellation before batch update
            if (token.isCancelled) {
                LogManager.service("Migration cancelled before batch update", "INFO")
                return OperationResult.error(s.shared("error_operation_cancelled"))
            }

            val updateResult = coordinator.processUserAction(
                "tool_data.batch_update",
                mapOf(
                    "toolInstanceId" to toolInstanceId,
                    "entries" to entriesToUpdate  // batch_update expects "entries" parameter
                    // partialValidation=true is applied automatically for batch_update
                )
            )

            if (updateResult.status != CommandStatus.SUCCESS) {
                val error = updateResult.error ?: s.shared("error_update_failed")
                LogManager.service("Batch update failed: $error", "ERROR")
                return OperationResult.error(error)
            }

            LogManager.service(
                "Migration completed successfully: $modifiedCount entries updated",
                "INFO"
            )

            return OperationResult.success(
                mapOf(
                    "modified_count" to modifiedCount,
                    "total_count" to entries.size
                )
            )

        } catch (e: Exception) {
            val error = "${s.shared("migration_failed")}: ${e.message}"
            LogManager.service("Migration exception: ${e.message}", "ERROR", e)
            return OperationResult.error(error)
        }
    }

    /**
     * Apply migration strategies to transform a custom_fields map.
     *
     * This function modifies the custom_fields map according to the migration
     * strategies, removing fields or values as needed.
     *
     * @param customFields Original custom_fields map (will be copied, not modified)
     * @param changes List of detected configuration changes
     * @param strategies Map of migration strategies for each change
     * @return Transformed custom_fields map
     */
    private fun applyMigrationStrategies(
        customFields: Map<String, Any?>,
        changes: List<FieldChange>,
        strategies: Map<FieldChange, MigrationStrategy>
    ): Map<String, Any?> {
        val result = customFields.toMutableMap()

        changes.forEach { change ->
            val strategy = strategies[change] ?: return@forEach

            when (strategy) {
                MigrationStrategy.STRIP_FIELD -> {
                    // Remove field from map completely
                    when (change) {
                        is FieldChange.Removed -> {
                            result.remove(change.name)
                        }
                        else -> {} // Strategy mismatch, should not happen
                    }
                }

                MigrationStrategy.STRIP_FIELD_IF_VALUE -> {
                    // Remove field only if value matches condition
                    when (change) {
                        is FieldChange.ChoiceOptionsRemoved -> {
                            val fieldValue = result[change.name]

                            // Check if value matches removed options
                            val shouldRemove = when (fieldValue) {
                                // Single choice: direct string comparison
                                is String -> change.removedOptions.contains(fieldValue)

                                // Multiple choice: check if any selected value was removed
                                is List<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val selectedOptions = fieldValue as? List<String> ?: emptyList()
                                    selectedOptions.any { it in change.removedOptions }
                                }

                                else -> false
                            }

                            if (shouldRemove) {
                                result.remove(change.name)
                            }
                        }
                        else -> {} // Strategy mismatch, should not happen
                    }
                }

                MigrationStrategy.NONE -> {
                    // No transformation needed
                }

                MigrationStrategy.ERROR -> {
                    // Should have been blocked before migration, log warning
                    LogManager.service(
                        "ERROR strategy encountered during migration (should be blocked): $change",
                        "WARN"
                    )
                }
            }
        }

        return result
    }
}
