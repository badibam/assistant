package app.treelune.core.fields.migration

/**
 * Determines migration strategies for custom field configuration changes.
 *
 * This policy object maps each type of detected change to an appropriate
 * migration strategy.
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

                // Remove the field only from the entries whose reference the field no longer takes
                is FieldChange.ReferenceTargetNarrowed -> MigrationStrategy.STRIP_FIELD_IF_VALUE
            }
        }
    }
}
