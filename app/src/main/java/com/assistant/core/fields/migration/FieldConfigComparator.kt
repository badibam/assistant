package com.assistant.core.fields.migration

import com.assistant.core.fields.ChoiceSettings
import com.assistant.core.fields.FieldDefinition

/**
 * Compares two custom_fields configurations to detect structural changes.
 *
 * This comparator analyzes differences between old and new field configurations
 * to identify changes that may require data migration (removals, type changes, etc.).
 *
 * Field identification:
 * - Fields are identified by their 'name' property (stable identifier)
 * - name changes cannot be detected directly (would appear as removed + added)
 * - displayName, description changes are cosmetic and tracked separately
 *
 * Detection logic:
 * 1. Fields in old but not in new → Removed
 * 2. Fields in new but not in old → Added
 * 3. Fields in both (same name):
 *    - Different type → TypeChanged
 *    - CHOICE with removed options → ChoiceOptionsRemoved
 *    - Only display/description/visibility changed → CosmeticChange
 *
 * Architecture:
 * - Pure function with no side effects
 * - Returns list of detected changes
 * - Used by both UI (CustomFieldsEditor) and AI (ToolInstanceService)
 */
object FieldConfigComparator {

    /**
     * Compare two custom field configurations and detect all changes.
     *
     * @param oldFields Previous field configuration
     * @param newFields New field configuration
     * @return List of detected changes (empty if no changes)
     */
    fun compare(
        oldFields: List<FieldDefinition>,
        newFields: List<FieldDefinition>
    ): List<FieldChange> {
        val changes = mutableListOf<FieldChange>()

        // Build maps keyed by field name (stable identifier)
        val oldFieldsMap = oldFields.associateBy { it.name }
        val newFieldsMap = newFields.associateBy { it.name }

        // Detect removed fields (in old, not in new)
        val removedNames = oldFieldsMap.keys - newFieldsMap.keys
        removedNames.forEach { name ->
            changes.add(FieldChange.Removed(name))
        }

        // Detect added fields (in new, not in old)
        val addedNames = newFieldsMap.keys - oldFieldsMap.keys
        addedNames.forEach { name ->
            val field = newFieldsMap[name]!!
            changes.add(FieldChange.Added(field))
        }

        // Detect changes in common fields (same name in both)
        val commonNames = oldFieldsMap.keys intersect newFieldsMap.keys
        commonNames.forEach { name ->
            val oldField = oldFieldsMap[name]!!
            val newField = newFieldsMap[name]!!

            // Check for type changes (not allowed)
            if (oldField.type != newField.type) {
                changes.add(
                    FieldChange.TypeChanged(
                        name = name,
                        oldType = oldField.type,
                        newType = newField.type
                    )
                )
                return@forEach // Type change is critical, skip other checks
            }

            // Type-specific config change detection
            when (oldField.type) {
                com.assistant.core.fields.FieldType.SCALE -> {
                    // Check for SCALE range changes (min or max)
                    val rangeChange = detectScaleRangeChange(oldField, newField)
                    if (rangeChange != null) {
                        changes.add(rangeChange)
                        return@forEach // Range change detected, skip other checks
                    }
                }

                com.assistant.core.fields.FieldType.CHOICE -> {
                    // Check for a CHOICE shape change (single, multiple, ranking)
                    val shapeChange = detectChoiceShapeChange(oldField, newField)
                    if (shapeChange != null) {
                        changes.add(shapeChange)
                        return@forEach // Shape changed, skip other checks
                    }

                    // Check for removed CHOICE options
                    val removedOptions = detectRemovedChoiceOptions(oldField, newField)
                    if (removedOptions.isNotEmpty()) {
                        changes.add(
                            FieldChange.ChoiceOptionsRemoved(
                                name = name,
                                removedOptions = removedOptions
                            )
                        )
                        return@forEach // Options removed, skip cosmetic check
                    }
                }

                com.assistant.core.fields.FieldType.REFERENCE -> {
                    // What it accepts changed: the values that no longer fit lose the field
                    if (oldField.config?.get(com.assistant.core.fields.ReferenceTarget.TARGET) !=
                        newField.config?.get(com.assistant.core.fields.ReferenceTarget.TARGET)) {
                        changes.add(FieldChange.ReferenceTargetNarrowed(name, newField.config))
                        return@forEach
                    }
                }

                else -> {
                    // Other types: no structural config changes to check
                }
            }

            // A key the type declares as restricting changed: the entries that no longer fit
            // lose the field, the others keep it. Asked of every type, so one added later is
            // covered by declaring its keys rather than by a branch above.
            val restrictingKeys = newField.type.restrictingConfigKeys
            if (restrictingKeys.isNotEmpty()) {
                val changed = restrictingKeys.any { key ->
                    oldField.config?.get(key) != newField.config?.get(key)
                }
                if (changed) {
                    changes.add(
                        FieldChange.ConfigRestricted(
                            name = name,
                            fieldType = newField.type,
                            newConfig = newField.config
                        )
                    )
                    return@forEach
                }
            }

            // Check for cosmetic changes only (display_name, description, always_visible)
            if (isCosmeticChange(oldField, newField)) {
                changes.add(FieldChange.CosmeticChange(name))
            }

            // No change detected if we reach here (config unchanged except cosmetic)
        }

        return changes
    }

    /**
     * Detect SCALE range changes (min or max modified).
     *
     * @param oldField Previous SCALE field definition
     * @param newField New SCALE field definition
     * @return ScaleRangeChanged if range changed, null otherwise
     */
    private fun detectScaleRangeChange(
        oldField: FieldDefinition,
        newField: FieldDefinition
    ): FieldChange.ScaleRangeChanged? {
        val oldMin = (oldField.config?.get("min") as? Number) ?: return null
        val oldMax = (oldField.config?.get("max") as? Number) ?: return null
        val newMin = (newField.config?.get("min") as? Number) ?: return null
        val newMax = (newField.config?.get("max") as? Number) ?: return null

        // Check if min or max changed
        if (oldMin.toDouble() != newMin.toDouble() || oldMax.toDouble() != newMax.toDouble()) {
            return FieldChange.ScaleRangeChanged(
                name = oldField.name,
                oldMin = oldMin,
                oldMax = oldMax,
                newMin = newMin,
                newMax = newMax
            )
        }

        return null
    }

    /**
     * Detects a CHOICE moving between one option, several and a ranking.
     */
    private fun detectChoiceShapeChange(
        oldField: FieldDefinition,
        newField: FieldDefinition
    ): FieldChange.ChoiceShapeChanged? {
        val oldShape = com.assistant.core.fields.ChoiceSettings.fromConfig(oldField.config).shape
        val newShape = com.assistant.core.fields.ChoiceSettings.fromConfig(newField.config).shape

        if (oldShape == newShape) return null
        return FieldChange.ChoiceShapeChanged(name = oldField.name, oldShape = oldShape, newShape = newShape)
    }

    /**
     * Detect removed options from a CHOICE field.
     *
     * @param oldField Previous CHOICE field definition
     * @param newField New CHOICE field definition
     * @return List of options present in old but not in new
     */
    private fun detectRemovedChoiceOptions(
        oldField: FieldDefinition,
        newField: FieldDefinition
    ): List<String> {
        // Extract options arrays from config
        val oldOptions = ChoiceSettings.fromConfig(oldField.config).options
        val newOptions = ChoiceSettings.fromConfig(newField.config).options

        // Find options in old but not in new
        return oldOptions - newOptions.toSet()
    }

    /**
     * Check if only cosmetic properties changed.
     *
     * Cosmetic properties (don't affect data):
     * - displayName
     * - description
     * - alwaysVisible
     *
     * Non-cosmetic properties (affect data structure/validation):
     * - name (identifier, cannot change)
     * - type (validation rules)
     * - config (type-specific validation)
     *
     * @param oldField Previous field definition
     * @param newField New field definition
     * @return true if only cosmetic properties changed
     */
    private fun isCosmeticChange(
        oldField: FieldDefinition,
        newField: FieldDefinition
    ): Boolean {
        // Check if cosmetic properties changed
        val cosmeticChanged = oldField.displayName != newField.displayName ||
                oldField.description != newField.description ||
                oldField.alwaysVisible != newField.alwaysVisible

        // Check if structural properties unchanged
        val structuralUnchanged = oldField.name == newField.name &&
                oldField.type == newField.type &&
                oldField.config == newField.config

        return cosmeticChanged && structuralUnchanged
    }
}
