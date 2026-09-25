package com.assistant.core.fields.migration

import android.content.Context
import com.assistant.core.fields.FieldDefinition
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
 * 3. If result is NeedsMigration → show MigrationConfirmationDialog
 * 4. On confirmation → save; the service migrates the entries
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

        // Check if any change requires data migration
        if (MigrationPolicy.requiresMigration(strategies)) {
            return MigrationCheckResult.NeedsMigration(changes, strategies)
        }

        // Only cosmetic changes (no migration needed)
        return MigrationCheckResult.NoMigration
    }
}

/**
 * Result of migration check operation.
 *
 * This sealed class represents the two possible outcomes when checking
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
}
