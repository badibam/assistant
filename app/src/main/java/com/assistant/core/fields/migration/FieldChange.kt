package com.assistant.core.fields.migration

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType

/**
 * Represents a detected change in custom fields configuration.
 *
 * When a tool instance's custom_fields configuration is modified, the system compares
 * the old and new configurations to detect structural changes that may impact existing data.
 *
 * Each change type has an associated MigrationStrategy that determines how to handle
 * existing data entries (none, strip field, error, etc.).
 *
 * Architecture:
 * - Changes detected by FieldConfigComparator.compare()
 * - Strategies determined by MigrationPolicy.getStrategies()
 * - Migration executed by FieldDataMigrator (Phase 2.3)
 */
sealed class FieldChange {
    /**
     * A new field was added to the configuration.
     * Strategy: NONE (no data migration needed)
     *
     * @param field The newly added field definition
     */
    data class Added(val field: FieldDefinition) : FieldChange()

    /**
     * An existing field was removed from the configuration.
     * Strategy: STRIP_FIELD (remove field from all existing entries)
     *
     * @param name The name of the removed field
     */
    data class Removed(val name: String) : FieldChange()

    /**
     * A field's name was changed.
     * Strategy: ERROR (renaming not allowed - name is the stable ID)
     *
     * Note: Field name is the stable identifier and cannot be changed.
     * This change is actually impossible to detect directly (name is the identifier),
     * but appears as Removed + Added in practice.
     *
     * @param oldName The original field name
     * @param newName The new field name
     */
    data class NameChanged(val oldName: String, val newName: String) : FieldChange()

    /**
     * A field's type was changed.
     * Strategy: ERROR (type changes not allowed - would corrupt existing values)
     *
     * @param name The field name
     * @param oldType The original field type
     * @param newType The new field type
     */
    data class TypeChanged(
        val name: String,
        val oldType: FieldType,
        val newType: FieldType
    ) : FieldChange()

    /**
     * Options were removed from a CHOICE field.
     * Strategy: STRIP_FIELD_IF_VALUE (remove field from entries using removed options)
     *
     * This affects entries where:
     * - Single choice: custom_fields[name] == removedOption
     * - Multiple choice: custom_fields[name] contains removedOption
     *
     * @param name The field name
     * @param removedOptions List of options that were removed from the field's config
     */
    data class ChoiceOptionsRemoved(
        val name: String,
        val removedOptions: List<String>
    ) : FieldChange()

    /**
     * A SCALE field's range was changed (min or max modified).
     * Strategy: STRIP_FIELD (remove field from all entries - values may be out of new range)
     *
     * Changing the scale range means existing values might be outside the new valid range.
     * Since we can't reliably determine which values are still valid, we remove the field
     * from all entries.
     *
     * @param name The field name
     * @param oldMin Previous minimum value
     * @param oldMax Previous maximum value
     * @param newMin New minimum value
     * @param newMax New maximum value
     */
    data class ScaleRangeChanged(
        val name: String,
        val oldMin: Number,
        val oldMax: Number,
        val newMin: Number,
        val newMax: Number
    ) : FieldChange()

    /**
     * A CHOICE field's multiple flag was changed (single ↔ multiple).
     * Strategy: STRIP_FIELD (remove field from all entries - data structure incompatible)
     *
     * Changing from single to multiple or vice versa changes the data structure:
     * - Single: value is a String
     * - Multiple: value is a List<String>
     *
     * Existing data would be in the wrong format, so we remove the field from all entries.
     *
     * @param name The field name
     * @param oldMultiple Previous multiple flag value
     * @param newMultiple New multiple flag value
     */
    data class ChoiceMultipleChanged(
        val name: String,
        val oldMultiple: Boolean,
        val newMultiple: Boolean
    ) : FieldChange()

    /**
     * Only cosmetic properties changed (display_name, description, placeholder, always_visible).
     * Strategy: NONE (no data migration needed)
     *
     * These changes don't affect data structure or validation, only how the field
     * is displayed in the UI. No migration required.
     *
     * @param name The field name
     */
    data class CosmeticChange(val name: String) : FieldChange()
}
