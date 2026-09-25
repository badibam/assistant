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
 * - TypeChanged → STRIP_FIELD (a value of the former type means nothing to the new one)
 * - ChoiceOptionsRemoved → STRIP_FIELD_IF_VALUE (conditional removal)
 * - CosmeticChange → NONE (no migration needed)
 *
 * Architecture:
 * - Pure strategy determination (no side effects)
 * - Localized descriptions via string system
 * - Used by EntryMigration, whoever changes the config
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

                // A value of the former type means nothing to the new one
                is FieldChange.TypeChanged -> MigrationStrategy.STRIP_FIELD

                // Remove field only from entries using removed options
                is FieldChange.ChoiceOptionsRemoved -> MigrationStrategy.STRIP_FIELD_IF_VALUE

                // Remove field from all entries when SCALE range changes
                // (existing values may be outside new range)
                is FieldChange.ScaleRangeChanged -> MigrationStrategy.STRIP_FIELD

                // Remove field from all entries when CHOICE multiple flag changes
                // (data structure incompatible: String vs List<String>)
                is FieldChange.ChoiceShapeChanged -> MigrationStrategy.STRIP_FIELD

                // No action needed for cosmetic changes
                is FieldChange.CosmeticChange -> MigrationStrategy.NONE

                // Remove the field only from the entries whose value no longer fits the config
                is FieldChange.ConfigRestricted -> MigrationStrategy.STRIP_FIELD_IF_VALUE
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
     * Displayed in the confirmation dialog before migration.
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
        val choiceShapeChangedCount = changes.count { it is FieldChange.ChoiceShapeChanged }
        val choiceOptionsRemovedCount = changes.count { it is FieldChange.ChoiceOptionsRemoved }
        val typeChangedCount = changes.count { it is FieldChange.TypeChanged }

        // Build description for each change type
        if (fieldRemovalCount > 0) {
            lines.add(s.shared("migration_fields_removed").format(fieldRemovalCount))
        }

        if (typeChangedCount > 0) {
            lines.add(s.shared("migration_type_changed").format(typeChangedCount))
        }

        if (scaleRangeChangedCount > 0) {
            lines.add(s.shared("migration_scale_range_changed").format(scaleRangeChangedCount))
        }

        if (choiceShapeChangedCount > 0) {
            lines.add(s.shared("migration_choice_shape_changed").format(choiceShapeChangedCount))
        }

        if (choiceOptionsRemovedCount > 0) {
            lines.add(s.shared("migration_choice_options_removed").format(choiceOptionsRemovedCount))
        }

        val configRestrictedCount = changes.count { it is FieldChange.ConfigRestricted }
        if (configRestrictedCount > 0) {
            lines.add(s.shared("migration_config_restricted").format(configRestrictedCount))
        }

        return if (lines.isEmpty()) {
            s.shared("migration_no_changes")
        } else {
            lines.joinToString("\n")
        }
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
