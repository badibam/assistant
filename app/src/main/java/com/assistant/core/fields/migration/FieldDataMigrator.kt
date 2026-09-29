package com.assistant.core.fields.migration

/**
 * What a configuration change does to the values of one entry's container ("data" or "extra"):
 * the strategies of the changes applied to its map. EntryMigration runs it over every entry.
 */
object FieldDataMigrator {

    /**
     * Apply migration strategies to the values of one container.
     *
     * @param values Original values of the container (copied, not modified)
     * @param changes List of detected configuration changes
     * @param strategies Map of migration strategies for each change
     * @param entryInstance The tool instance of an entry a reference designates, null when the
     *   entry no longer exists: a deleted target keeps its reference
     * @return The values the container keeps
     *
     * Visible to the tests: this is where a configuration change meets real entries, and what it
     * does to them cannot be read off the strategies alone.
     */
    internal fun applyMigrationStrategies(
        values: Map<String, Any?>,
        changes: List<FieldChange>,
        strategies: Map<FieldChange, MigrationStrategy>,
        entryInstance: (String) -> String? = { null }
    ): Map<String, Any?> {
        val result = values.toMutableMap()

        changes.forEach { change ->
            val strategy = strategies[change] ?: return@forEach

            when (strategy) {
                MigrationStrategy.STRIP_FIELD -> {
                    // Remove the field from the entry, whatever made the strategy apply: the
                    // field being deleted or changing type, a scale whose range no longer means
                    // the same thing, a choice that changed between one value and a list.
                    when (change) {
                        is FieldChange.Removed -> result.remove(change.name)
                        is FieldChange.TypeChanged -> result.remove(change.name)
                        is FieldChange.ScaleRangeChanged -> result.remove(change.name)
                        is FieldChange.ChoiceShapeChanged -> result.remove(change.name)
                        else -> {} // Strategy mismatch, should not happen
                    }
                }

                MigrationStrategy.STRIP_FIELD_IF_VALUE -> {
                    // Remove field only if value matches condition
                    when (change) {
                        is FieldChange.ChoiceOptionsRemoved -> {
                            val fieldValue = result[change.name]

                            // Check if value matches removed options
                            val shouldRemove = when (fieldValue) {
                                // Single choice: direct string comparison
                                is String -> change.removedOptions.contains(fieldValue)

                                // Multiple choice: check if any selected value was removed
                                is List<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val selectedOptions = fieldValue as? List<String> ?: emptyList()
                                    selectedOptions.any { it in change.removedOptions }
                                }

                                else -> false
                            }

                            if (shouldRemove) {
                                result.remove(change.name)
                            }
                        }
                        is FieldChange.ConfigRestricted -> {
                            // The type says whether the stored value still fits. A widened bound
                            // reaches here too, and keeps everything.
                            if (!change.fieldType.permits(result[change.name], change.newConfig)) {
                                result.remove(change.name)
                            }
                        }
                        is FieldChange.ReferenceTargetNarrowed -> {
                            val reference = com.assistant.core.fields.ReferenceTarget.referenceOf(result[change.name])
                            val target = com.assistant.core.fields.ReferenceTarget.fromConfig(change.newConfig)
                            val fits = reference == null || (target.acceptsKind(reference) &&
                                (reference.kind != com.assistant.core.selection.ReferenceKind.ENTRY ||
                                    entryInstance(reference.id!!)?.let { target.acceptsEntryOf(it) } ?: true))
                            if (!fits) result.remove(change.name)
                        }
                        else -> {} // Strategy mismatch, should not happen
                    }
                }

                MigrationStrategy.NONE -> {
                    // No transformation needed
                }
            }
        }

        return result
    }
}
