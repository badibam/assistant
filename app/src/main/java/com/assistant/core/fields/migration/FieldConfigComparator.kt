package com.assistant.core.fields.migration

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
                    // Check for CHOICE multiple flag change
                    val multipleChange = detectChoiceMultipleChange(oldField, newField)
                    if (multipleChange != null) {
                        changes.add(multipleChange)
                        return@forEach // Multiple flag changed, skip other checks
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

                else -> {
                    // Other types: no structural config changes to check
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
     * Detect CHOICE multiple flag change (single ↔ multiple).
     *
     * @param oldField Previous CHOICE field definition
     * @param newField New CHOICE field definition
     * @return ChoiceMultipleChanged if multiple flag changed, null otherwise
     */
    private fun detectChoiceMultipleChange(
        oldField: FieldDefinition,
        newField: FieldDefinition
    ): FieldChange.ChoiceMultipleChanged? {
        // Get multiple flag (defaults to false if not specified)
        val oldMultiple = (oldField.config?.get("multiple") as? Boolean) ?: false
        val newMultiple = (newField.config?.get("multiple") as? Boolean) ?: false

        // Check if multiple flag changed
        if (oldMultiple != newMultiple) {
            return FieldChange.ChoiceMultipleChanged(
                name = oldField.name,
                oldMultiple = oldMultiple,
                newMultiple = newMultiple
            )
        }

        return null
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
        val oldOptions = (oldField.config?.get("options") as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()

        val newOptions = (newField.config?.get("options") as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()

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
