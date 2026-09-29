package com.assistant.core.fields.migration

import com.assistant.core.database.entities.ToolDataEntity
import com.assistant.core.fields.EntrySchemaGenerator
import com.assistant.core.fields.FieldContainer
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FixedField
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * What a change of a tool's config does to the entries already recorded
 * (docs/DATA.md).
 *
 * Nothing is declared for it: the fields the old config gives the entries are compared with the
 * ones the new config gives them, "data" (the tool type's fixed fields, from getEntryFields) and
 * "extra" (the user's fields) alike, and each change says which stored values keep their meaning
 * (MigrationPolicy). A value that loses it is removed; an entry that loses the value of a
 * required field of "data" is deleted, since it would no longer be a valid entry. Nothing is
 * invented: an entry lacking a value a field now requires takes the one whoever acts gave for
 * that field, and is counted when none was given.
 *
 * Pure: the caller loads the entries and writes the plan.
 */
object EntryMigration {

    /** The fields a config gives the entries of its tool. */
    data class Fields(val data: List<FixedField>, val extra: List<FieldDefinition>)

    /**
     * @property updated The kept entries whose content changes, rewritten
     * @property deleted The entries that lost the value of a required field
     * @property removedValues The values removed from the kept entries
     * @property filledValues The values given to kept entries for a field now required
     * @property missing For each field of "data" now required and given no value, the entries without one
     */
    data class Plan(
        val updated: List<ToolDataEntity>,
        val deleted: List<ToolDataEntity>,
        val removedValues: Int,
        val filledValues: Int,
        val missing: Map<String, Int>
    ) {
        /** Whether applying it loses something recorded: only an explicit agreement lets it through. */
        val losesData: Boolean get() = removedValues > 0 || deleted.isNotEmpty()
    }

    /**
     * @param fill For a field of "data" now required, by name, the value an entry without one takes
     * @param entryInstance The tool instance of an entry a reference designates, null once deleted
     */
    fun plan(
        old: Fields,
        new: Fields,
        entries: List<ToolDataEntity>,
        fill: Map<String, Any> = emptyMap(),
        entryInstance: (String) -> String? = { null }
    ): Plan {
        val dataChanges = FieldConfigComparator.compare(old.data.map { it.definition }, new.data.map { it.definition })
        val extraChanges = FieldConfigComparator.compare(old.extra, new.extra)
        val dataStrategies = MigrationPolicy.getStrategies(dataChanges)
        val extraStrategies = MigrationPolicy.getStrategies(extraChanges)
        val required = new.data.filter { it.required }.map { it.definition.name }

        val updated = mutableListOf<ToolDataEntity>()
        val deleted = mutableListOf<ToolDataEntity>()
        var removedValues = 0
        var filledValues = 0
        val missing = mutableMapOf<String, Int>()

        entries.forEach { entry ->
            val state = entry.state?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            val data = JsonUtils.toMap(entry.data)
            val extra = JsonUtils.toMap(entry.extra?.takeIf { it.isNotBlank() })

            val newData = FieldDataMigrator.applyMigrationStrategies(data, dataChanges, dataStrategies, entryInstance).toMutableMap()
            val newExtra = FieldDataMigrator.applyMigrationStrategies(extra, extraChanges, extraStrategies, entryInstance)

            // A running DURATION holds its value in the state until stopped: it counts as a value,
            // and it goes with the field when the field loses its values
            val newState = state?.let { JSONObject(it.toString()) }
            val removedData = removed(data, newData, state, newState, FieldContainer.DATA, dataChanges, dataStrategies)
            val removedExtra = removed(extra, newExtra, state, newState, FieldContainer.EXTRA, extraChanges, extraStrategies)

            if (removedData.any { it in required }) {
                deleted.add(entry)
                return@forEach
            }
            removedValues += removedData.size + removedExtra.size

            var filled = false
            required.filter { name -> newData[name] == null && startedAt(newState, FieldContainer.DATA, name) == null }
                .forEach { name ->
                    val given = fill[name]
                    if (given != null) {
                        newData[name] = given
                        filledValues++
                        filled = true
                    } else {
                        missing.merge(name, 1, Int::plus)
                    }
                }

            if (removedData.isNotEmpty() || removedExtra.isNotEmpty() || filled) {
                updated.add(entry.copy(
                    data = JsonUtils.toJSONObject(newData).toString(),
                    extra = newExtra.takeIf { it.isNotEmpty() }?.let { JsonUtils.toJSONObject(it).toString() },
                    state = newState?.takeIf { it.length() > 0 }?.toString()
                ))
            }
        }

        return Plan(updated, deleted, removedValues, filledValues, missing)
    }

    /**
     * The fields of [container] that had a value before and have none after, a stored one or a
     * running one; the running ones that lose their value are taken out of [newState].
     */
    private fun removed(
        before: Map<String, Any?>,
        after: Map<String, Any?>,
        state: JSONObject?,
        newState: JSONObject?,
        container: FieldContainer,
        changes: List<FieldChange>,
        strategies: Map<FieldChange, MigrationStrategy>
    ): Set<String> {
        val stored = before.keys.filter { before[it] != null && after[it] == null }.toSet()
        // A running field has no stored value to lose: whether it keeps its meaning is whether
        // the change strips the field outright (the type changed, the field is gone)
        val stripped = changes.filter { strategies[it] == MigrationStrategy.STRIP_FIELD }.mapNotNull { it.fieldName }.toSet()
        val running = stripped.filter { startedAt(state, container, it) != null }.toSet()
        running.forEach { name ->
            val byContainer = newState?.optJSONObject(EntrySchemaGenerator.RUNNING_KEY)
            byContainer?.optJSONObject(container.key)?.let { fields ->
                fields.remove(name)
                if (fields.length() == 0) byContainer.remove(container.key)
            }
            if (byContainer != null && byContainer.length() == 0) newState.remove(EntrySchemaGenerator.RUNNING_KEY)
        }
        return stored + running
    }

    private fun startedAt(state: JSONObject?, container: FieldContainer, field: String): Long? =
        com.assistant.core.fields.RunningDurations.startedAt(state, container, field)

    /** The field a change is about. */
    private val FieldChange.fieldName: String?
        get() = when (this) {
            is FieldChange.Added -> field.name
            is FieldChange.Removed -> name
            is FieldChange.TypeChanged -> name
            is FieldChange.ChoiceOptionsRemoved -> name
            is FieldChange.ScaleRangeChanged -> name
            is FieldChange.ChoiceShapeChanged -> name
            is FieldChange.CosmeticChange -> name
            is FieldChange.ConfigRestricted -> name
            is FieldChange.ReferenceTargetNarrowed -> name
        }
}
