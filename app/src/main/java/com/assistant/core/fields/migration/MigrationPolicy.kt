package com.assistant.core.fields.migration

import android.content.Context
import com.assistant.core.strings.Strings

/**
 * Determines migration strategies for custom field configuration changes.
 *
 * This policy object maps each type of detected change to an appropriate
 * migration strategy, and generates user-friendly descriptions of what
 * will happen when the migration is applied.
 *
 * Strategy mapping:
 * - Added → NONE (no migration needed)
 * - Removed → STRIP_FIELD (remove from all entries)
 * - NameChanged → ERROR (forbidden)
 * - TypeChanged → ERROR (forbidden)
 * - ChoiceOptionsRemoved → STRIP_FIELD_IF_VALUE (conditional removal)
 * - CosmeticChange → NONE (no migration needed)
 *
 * Architecture:
 * - Pure strategy determination (no side effects)
 * - Localized descriptions via string system
 * - Used by both UI (CustomFieldsEditor) and AI (ToolInstanceService)
 */
object MigrationPolicy {

    /**
     * Determine the migration strategy for each detected change.
     *
     * This function maps each FieldChange to its appropriate MigrationStrategy
     * based on the type of change and its impact on existing data.
     *
     * @param changes List of detected changes from FieldConfigComparator
     * @return Map of change → strategy for each detected change
     */
    fun getStrategies(
        changes: List<FieldChange>
    ): Map<FieldChange, MigrationStrategy> {
        return changes.associateWith { change ->
            when (change) {
                // No action needed for additions (existing entries unaffected)
                is FieldChange.Added -> MigrationStrategy.NONE

                // Remove field from all entries when field is deleted
                is FieldChange.Removed -> MigrationStrategy.STRIP_FIELD

                // Forbid name changes (name is the stable identifier)
                is FieldChange.NameChanged -> MigrationStrategy.ERROR

                // Forbid type changes (would corrupt existing values)
                is FieldChange.TypeChanged -> MigrationStrategy.ERROR

                // Remove field only from entries using removed options
                is FieldChange.ChoiceOptionsRemoved -> MigrationStrategy.STRIP_FIELD_IF_VALUE

                // Remove field from all entries when SCALE range changes
                // (existing values may be outside new range)
                is FieldChange.ScaleRangeChanged -> MigrationStrategy.STRIP_FIELD

                // Remove field from all entries when CHOICE multiple flag changes
                // (data structure incompatible: String vs List<String>)
                is FieldChange.ChoiceMultipleChanged -> MigrationStrategy.STRIP_FIELD

                // No action needed for cosmetic changes
                is FieldChange.CosmeticChange -> MigrationStrategy.NONE
            }
        }
    }

    /**
     * Generate a user-friendly description of the migration that will occur.
     *
     * This description explains:
     * - What changes were detected
     * - What data will be affected
     * - What actions will be taken
     *
     * Used by:
     * - UI: Displayed in confirmation dialog before migration
     * - AI: Returned as error message when strategy is ERROR
     *
     * @param changes List of detected changes
     * @param strategies Map of strategies for each change
     * @param context Android context for string access
     * @return Localized description of migration actions
     */
    fun getDescription(
        changes: List<FieldChange>,
        strategies: Map<FieldChange, MigrationStrategy>,
        context: Context
    ): String {
        val s = Strings.`for`(context = context)
        val lines = mutableListOf<String>()

        // Count changes by type for user-friendly grouping
        val fieldRemovalCount = changes.count { it is FieldChange.Removed }
        val scaleRangeChangedCount = changes.count { it is FieldChange.ScaleRangeChanged }
        val choiceMultipleChangedCount = changes.count { it is FieldChange.ChoiceMultipleChanged }
        val choiceOptionsRemovedCount = changes.count { it is FieldChange.ChoiceOptionsRemoved }
        val errorCount = changes.count { strategies[it] == MigrationStrategy.ERROR }

        // Build description for each change type
        if (fieldRemovalCount > 0) {
            lines.add(s.shared("migration_fields_removed").format(fieldRemovalCount))
        }

        if (scaleRangeChangedCount > 0) {
            lines.add(s.shared("migration_scale_range_changed").format(scaleRangeChangedCount))
        }

        if (choiceMultipleChangedCount > 0) {
            lines.add(s.shared("migration_choice_multiple_changed").format(choiceMultipleChangedCount))
        }

        if (choiceOptionsRemovedCount > 0) {
            lines.add(s.shared("migration_choice_options_removed").format(choiceOptionsRemovedCount))
        }

        if (errorCount > 0) {
            // List specific errors
            changes.filter { strategies[it] == MigrationStrategy.ERROR }.forEach { change ->
                val errorMessage = when (change) {
                    is FieldChange.NameChanged ->
                        s.shared("error_field_name_changed")

                    is FieldChange.TypeChanged ->
                        s.shared("error_field_type_changed")

                    else -> s.shared("error_migration_blocked")
                }
                lines.add(errorMessage)
            }
        }

        return if (lines.isEmpty()) {
            s.shared("migration_no_changes")
        } else {
            lines.joinToString("\n")
        }
    }

    /**
     * Check if any change has an ERROR strategy.
     *
     * Used to quickly determine if the configuration change should be blocked.
     *
     * @param strategies Map of strategies for detected changes
     * @return true if any strategy is ERROR
     */
    fun hasErrorStrategy(strategies: Map<FieldChange, MigrationStrategy>): Boolean {
        return strategies.values.any { it == MigrationStrategy.ERROR }
    }

    /**
     * Check if any change requires data migration.
     *
     * Used to determine if FieldDataMigrator needs to be invoked.
     *
     * @param strategies Map of strategies for detected changes
     * @return true if any strategy requires migration (STRIP_FIELD or STRIP_FIELD_IF_VALUE)
     */
    fun requiresMigration(strategies: Map<FieldChange, MigrationStrategy>): Boolean {
        return strategies.values.any {
            it == MigrationStrategy.STRIP_FIELD || it == MigrationStrategy.STRIP_FIELD_IF_VALUE
        }
    }
}
