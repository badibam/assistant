package com.assistant.core.fields.migration

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings

/**
 * Helper for custom fields migration workflow in UI contexts.
 *
 * This helper provides a high-level API for managing the migration flow when
 * saving tool configuration with modified custom fields. It encapsulates the
 * detection, validation, and execution of migrations.
 *
 * Usage in ConfigScreens:
 * 1. Before save, call checkMigrationNeeded() with old/new custom_fields
 * 2. If result is NoMigration → save directly
 * 3. If result is Error → show error, block save
 * 4. If result is NeedsMigration → show MigrationConfirmationDialog
 * 5. On confirmation → call executeMigration(), then save if success
 *
 * Architecture:
 * - Stateless helper functions
 * - Uses FieldConfigComparator, MigrationPolicy, FieldDataMigrator
 * - Provides sealed class result for clear control flow
 */
object FieldMigrationHelper {

    /**
     * Check if migration is needed for custom fields changes.
     *
     * This function detects changes between old and new custom field configurations
     * and determines if migration is needed, blocked, or unnecessary.
     *
     * @param oldFields Previous custom fields configuration
     * @param newFields New custom fields configuration
     * @param context Android context for string translation
     * @return MigrationCheckResult indicating what action to take
     */
    fun checkMigrationNeeded(
        oldFields: List<FieldDefinition>,
        newFields: List<FieldDefinition>,
        context: Context
    ): MigrationCheckResult {
        // Detect all changes
        val changes = FieldConfigComparator.compare(oldFields, newFields)

        // No changes detected
        if (changes.isEmpty()) {
            return MigrationCheckResult.NoMigration
        }

        // Determine migration strategies for each change
        val strategies = MigrationPolicy.getStrategies(changes)

        // Check if any change has ERROR strategy (blocks save)
        if (MigrationPolicy.hasErrorStrategy(strategies)) {
            val errorMessage = MigrationPolicy.getDescription(changes, strategies, context)
            return MigrationCheckResult.Error(errorMessage)
        }

        // Check if any change requires data migration
        if (MigrationPolicy.requiresMigration(strategies)) {
            return MigrationCheckResult.NeedsMigration(changes, strategies)
        }

        // Only cosmetic changes (no migration needed)
        return MigrationCheckResult.NoMigration
    }

    /**
     * Execute the migration after user confirmation.
     *
     * This function should be called after the user has confirmed the migration
     * in the MigrationConfirmationDialog. It applies the migration strategies
     * to existing data entries.
     *
     * @param coordinator CommandDispatcher coordinator
     * @param toolInstanceId Tool instance to migrate
     * @param changes List of detected changes
     * @param strategies Map of migration strategies
     * @param context Android context for string access
     * @param token Cancellation token for abort support
     * @return OperationResult with success status
     */
    suspend fun executeMigration(
        coordinator: Coordinator,
        toolInstanceId: String,
        changes: List<FieldChange>,
        strategies: Map<FieldChange, MigrationStrategy>,
        context: Context,
        token: CancellationToken
    ): OperationResult {
        return FieldDataMigrator.migrateCustomFields(
            coordinator = coordinator,
            toolInstanceId = toolInstanceId,
            changes = changes,
            strategies = strategies,
            context = context,
            token = token
        )
    }
}

/**
 * Result of migration check operation.
 *
 * This sealed class represents the three possible outcomes when checking
 * if migration is needed for custom fields changes.
 */
sealed class MigrationCheckResult {
    /**
     * No migration needed.
     * Either no changes detected, or only cosmetic changes.
     * Action: Save configuration directly.
     */
    object NoMigration : MigrationCheckResult()

    /**
     * Migration is needed.
     * Some changes require data migration (field removals, option removals).
     * Action: Show MigrationConfirmationDialog, execute migration if confirmed.
     *
     * @param changes List of detected changes
     * @param strategies Map of migration strategies for each change
     */
    data class NeedsMigration(
        val changes: List<FieldChange>,
        val strategies: Map<FieldChange, MigrationStrategy>
    ) : MigrationCheckResult()

    /**
     * Migration is blocked due to forbidden changes.
     * Some changes have ERROR strategy (name changes, type changes).
     * Action: Show error message, block configuration save.
     *
     * @param errorMessage Translated error message explaining what's wrong
     */
    data class Error(val errorMessage: String) : MigrationCheckResult()
}
