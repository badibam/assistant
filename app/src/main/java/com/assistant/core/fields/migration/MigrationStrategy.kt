package com.assistant.core.fields.migration

/**
 * Strategy to apply when migrating data after a custom field configuration change.
 *
 * Each FieldChange type has an associated MigrationStrategy that determines
 * how to handle existing data entries in the database.
 *
 * Strategies are determined automatically by MigrationPolicy.getStrategies()
 * based on the type of change detected. The migration is then executed by
 * FieldDataMigrator which applies the strategy to all affected entries.
 *
 * Architecture:
 * - NONE: No action needed (cosmetic changes, field additions)
 * - STRIP_FIELD: Remove field from all entries (field deletion)
 * - STRIP_FIELD_IF_VALUE: Conditional removal (removed CHOICE options)
 * - ERROR: Block the configuration change (name/type changes)
 */
enum class MigrationStrategy {
    /**
     * No migration action required.
     *
     * Applied to:
     * - FieldChange.Added: New fields don't affect existing entries
     * - FieldChange.CosmeticChange: Display changes don't affect data
     *
     * Result: Configuration saved directly, no data modifications
     */
    NONE,

    /**
     * Remove the field from all existing entries.
     *
     * Applied to:
     * - FieldChange.Removed: Field deleted from configuration
     *
     * Result: custom_fields[fieldName] removed from all tool_data entries
     *
     * Example:
     * Before: {"custom_fields": {"mood": "happy", "notes": "Good day"}}
     * After:  {"custom_fields": {"mood": "happy"}} (if "notes" was removed)
     */
    STRIP_FIELD,

    /**
     * Remove the field only from entries with specific values.
     *
     * Applied to:
     * - FieldChange.ChoiceOptionsRemoved: Some CHOICE options removed
     *
     * Result: custom_fields[fieldName] removed only if value matches removed option
     *
     * Example - Single choice:
     * Before: {"custom_fields": {"mood": "sad"}}
     * After:  {"custom_fields": {}} (if "sad" was removed from options)
     *
     * Example - Multiple choice:
     * Before: {"custom_fields": {"tags": ["work", "urgent", "review"]}}
     * After:  {"custom_fields": {}} (if "urgent" was removed and is in the list)
     */
    STRIP_FIELD_IF_VALUE,

    /**
     * Block the configuration change with an error.
     *
     * Applied to:
     * - FieldChange.NameChanged: Field name is the stable ID, cannot change
     * - FieldChange.TypeChanged: Type changes would corrupt existing values
     *
     * Result: Configuration save is rejected with an error message
     *
     * Rationale:
     * - Name changes: Would lose all data (name is the identifier)
     * - Type changes: Would create invalid data (e.g., text in numeric field)
     */
    ERROR
}
