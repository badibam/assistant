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
     * - FieldChange.TypeChanged: a value of the former type means nothing to the new one
     *
     * Result: custom_fields[fieldName] removed from all tool_data entries
     *
     * Example:
     * Before: {"extra": {"mood": "happy", "notes": "Good day"}}
     * After:  {"extra": {"mood": "happy"}} (if "notes" was removed)
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
     * Before: {"extra": {"mood": "sad"}}
     * After:  {"extra": {}} (if "sad" was removed from options)
     *
     * Example - Multiple choice:
     * Before: {"extra": {"tags": ["work", "urgent", "review"]}}
     * After:  {"extra": {}} (if "urgent" was removed and is in the list)
     */
    STRIP_FIELD_IF_VALUE
}
