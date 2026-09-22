package com.assistant.core.fields.migration

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers what the comparator sees when a tool's custom fields are reconfigured, and what
 * MigrationPolicy then decides to do with the data already recorded under them.
 *
 * This is the pair that decides whether a user's history survives an edit to a form, so the
 * cases worth writing down are the ones where it does not.
 */
class FieldConfigComparatorTest {

    private fun field(
        name: String,
        type: FieldType = FieldType.TEXT,
        displayName: String = name,
        description: String? = null,
        alwaysVisible: Boolean = false,
        config: Map<String, Any>? = null
    ) = FieldDefinition(
        name = name,
        displayName = displayName,
        description = description,
        type = type,
        alwaysVisible = alwaysVisible,
        config = config
    )

    private fun scale(name: String, min: Int, max: Int, minLabel: String? = null) = field(
        name = name,
        type = FieldType.SCALE,
        config = buildMap {
            put("min", min)
            put("max", max)
            if (minLabel != null) put("min_label", minLabel)
        }
    )

    private fun choice(name: String, options: List<String>, multiple: Boolean = false) = field(
        name = name,
        type = FieldType.CHOICE,
        config = mapOf("options" to options, "multiple" to multiple)
    )

    private fun strategyFor(change: FieldChange) =
        MigrationPolicy.getStrategies(listOf(change))[change]

    // ==================== Nothing happened ====================

    /** An untouched configuration produces no change, and so no migration. */
    @Test
    fun anUnchangedConfiguration_producesNothing() {
        val fields = listOf(field("mood"), scale("energy", 1, 10))

        assertEquals(emptyList<FieldChange>(), FieldConfigComparator.compare(fields, fields))
    }

    // ==================== Adding and removing ====================

    /** A new field leaves the recorded entries alone: they simply have nothing under it. */
    @Test
    fun addingAField_needsNoMigration() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(field("mood")),
            newFields = listOf(field("mood"), field("notes"))
        )

        assertEquals(1, changes.size)
        assertEquals("notes", (changes.single() as FieldChange.Added).field.name)
        assertEquals(MigrationStrategy.NONE, strategyFor(changes.single()))
    }

    /** Deleting a field takes its recorded values with it. That is the point of deleting it. */
    @Test
    fun removingAField_stripsItFromEveryEntry() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(field("mood"), field("notes")),
            newFields = listOf(field("mood"))
        )

        assertEquals(FieldChange.Removed("notes"), changes.single())
        assertEquals(MigrationStrategy.STRIP_FIELD, strategyFor(changes.single()))
    }

    /**
     * Renaming a field destroys its history.
     *
     * Fields are identified by their technical name, so a rename is not seen as a rename: it
     * is one field gone and another arrived. The departure strips the recorded values, the
     * arrival adds an empty field, and the entries come out blank under the new name.
     *
     * This states what the code does today. TODO.md carries the rename-aware migration this
     * calls for; the test is here so the day it is written, this stops passing.
     */
    @Test
    fun renamingAField_readsAsADeletionAndLosesTheHistory() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(field(name = "mood", displayName = "Mood")),
            newFields = listOf(field(name = "feeling", displayName = "Mood"))
        )

        assertEquals(2, changes.size)
        assertTrue(changes.contains(FieldChange.Removed("mood")))
        assertTrue(changes.any { it is FieldChange.Added && it.field.name == "feeling" })

        val strategies = MigrationPolicy.getStrategies(changes)
        assertTrue(MigrationPolicy.requiresMigration(strategies))
        assertEquals(
            MigrationStrategy.STRIP_FIELD,
            strategies[FieldChange.Removed("mood")]
        )
    }

    // ==================== Changing a field's type ====================

    /** Changing a type is refused outright rather than migrated. */
    @Test
    fun changingAFieldsType_isBlocked() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(field("count", type = FieldType.TEXT)),
            newFields = listOf(field("count", type = FieldType.NUMERIC))
        )

        val typeChanged = changes.single() as FieldChange.TypeChanged
        assertEquals(FieldType.TEXT, typeChanged.oldType)
        assertEquals(FieldType.NUMERIC, typeChanged.newType)

        val strategies = MigrationPolicy.getStrategies(changes)
        assertEquals(MigrationStrategy.ERROR, strategies[typeChanged])
        assertTrue(MigrationPolicy.hasErrorStrategy(strategies))
    }

    /** A type change stops the comparison for that field: nothing else about it is reported. */
    @Test
    fun changingTypeAndTheRest_reportsOnlyTheTypeChange() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(scale("level", 1, 10)),
            newFields = listOf(field("level", type = FieldType.NUMERIC, displayName = "Level!"))
        )

        assertEquals(1, changes.size)
        assertTrue(changes.single() is FieldChange.TypeChanged)
    }

    // ==================== Scales and choices ====================

    /** Moving a scale's bounds drops its recorded values, which may now sit outside them. */
    @Test
    fun changingAScalesRange_stripsTheField() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(scale("energy", 1, 10)),
            newFields = listOf(scale("energy", 1, 5))
        )

        val ranged = changes.single() as FieldChange.ScaleRangeChanged
        assertEquals(10, ranged.oldMax)
        assertEquals(5, ranged.newMax)
        assertEquals(MigrationStrategy.STRIP_FIELD, strategyFor(ranged))
    }

    /** Switching a choice between one answer and several changes the shape of what is stored. */
    @Test
    fun switchingAChoiceToMultiple_stripsTheField() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(choice("tags", listOf("work", "home"), multiple = false)),
            newFields = listOf(choice("tags", listOf("work", "home"), multiple = true))
        )

        val switched = changes.single() as FieldChange.ChoiceMultipleChanged
        assertFalse(switched.oldMultiple)
        assertTrue(switched.newMultiple)
        assertEquals(MigrationStrategy.STRIP_FIELD, strategyFor(switched))
    }

    /** Dropping an option only affects the entries that had chosen it. */
    @Test
    fun removingChoiceOptions_stripsOnlyTheEntriesUsingThem() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(choice("tags", listOf("work", "home", "urgent"))),
            newFields = listOf(choice("tags", listOf("work", "home")))
        )

        val removed = changes.single() as FieldChange.ChoiceOptionsRemoved
        assertEquals(listOf("urgent"), removed.removedOptions)
        assertEquals(MigrationStrategy.STRIP_FIELD_IF_VALUE, strategyFor(removed))
    }

    /** Adding an option changes nothing that was already recorded. */
    @Test
    fun addingAChoiceOption_producesNothing() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(choice("tags", listOf("work"))),
            newFields = listOf(choice("tags", listOf("work", "home")))
        )

        assertEquals(emptyList<FieldChange>(), changes)
    }

    // ==================== Renaming what the user sees ====================

    /** Retitling a field, or describing it, touches no data. */
    @Test
    fun changingWhatIsDisplayed_needsNoMigration() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(field("mood", displayName = "Mood")),
            newFields = listOf(field("mood", displayName = "How I felt", description = "daily"))
        )

        assertEquals(FieldChange.CosmeticChange("mood"), changes.single())
        assertEquals(MigrationStrategy.NONE, strategyFor(changes.single()))
    }

    // ==================== A config that restricts what is allowed ====================

    /**
     * A type declares which of its config keys restrict the values allowed, and a change to one
     * of them is reported so the entries that no longer fit lose the field. The others keep it:
     * unlike a scale's range, a text length or a numeric bound does not change what a stored
     * value means.
     */
    @Test
    fun narrowingATextOrNumericConfig_isReported() {
        val textShrunk = FieldConfigComparator.compare(
            oldFields = listOf(field("notes", config = mapOf("length" to "UNLIMITED"))),
            newFields = listOf(field("notes", config = mapOf("length" to "SHORT")))
        )
        assertTrue(textShrunk.any { it is FieldChange.ConfigRestricted && it.name == "notes" })

        val numericNarrowed = FieldConfigComparator.compare(
            oldFields = listOf(field("weight", type = FieldType.NUMERIC, config = mapOf("min" to 0, "max" to 500))),
            newFields = listOf(field("weight", type = FieldType.NUMERIC, config = mapOf("min" to 0, "max" to 100)))
        )
        assertTrue(numericNarrowed.any { it is FieldChange.ConfigRestricted && it.name == "weight" })

        val strategies = MigrationPolicy.getStrategies(numericNarrowed)
        assertEquals(
            listOf(MigrationStrategy.STRIP_FIELD_IF_VALUE),
            strategies.values.toList()
        )
    }

    /**
     * A scale's labels are part of its config but not part of its range, so changing them
     * is reported as nothing rather than as a cosmetic change -- the cosmetic test requires
     * the config to be identical, and it is not.
     *
     * Harmless in itself, both meaning no migration. Recorded because it is the same blind
     * spot as above, seen from the other side.
     */
    @Test
    fun changingAScalesLabels_producesNothing() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(scale("energy", 1, 10, minLabel = "flat")),
            newFields = listOf(scale("energy", 1, 10, minLabel = "exhausted"))
        )

        assertEquals(emptyList<FieldChange>(), changes)
    }

    /**
     * A retitling that comes with a restricting change is swallowed by it: only the restriction
     * is reported. Harmless, both being about the same field -- the restriction is what decides
     * the data, and renaming decides nothing.
     */
    @Test
    fun retitlingAlongsideARestrictingChange_reportsOnlyTheRestriction() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(field("notes", displayName = "Notes", config = mapOf("length" to "UNLIMITED"))),
            newFields = listOf(field("notes", displayName = "Remarks", config = mapOf("length" to "SHORT")))
        )

        assertEquals(1, changes.size)
        assertTrue(changes.single() is FieldChange.ConfigRestricted)
    }

    // ==================== Several fields at once ====================

    /** Changes to different fields are reported together, each with its own strategy. */
    @Test
    fun severalFieldsChangingAtOnce_areAllReported() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(field("mood"), field("notes"), scale("energy", 1, 10)),
            newFields = listOf(
                field("mood", displayName = "How I felt"),
                scale("energy", 1, 5),
                field("sleep")
            )
        )

        assertEquals(4, changes.size)
        assertTrue(changes.contains(FieldChange.Removed("notes")))
        assertTrue(changes.contains(FieldChange.CosmeticChange("mood")))
        assertTrue(changes.any { it is FieldChange.Added && it.field.name == "sleep" })
        assertTrue(changes.any { it is FieldChange.ScaleRangeChanged })

        val strategies = MigrationPolicy.getStrategies(changes)
        assertFalse(MigrationPolicy.hasErrorStrategy(strategies))
        assertTrue(MigrationPolicy.requiresMigration(strategies))
    }

    /** With nothing but additions and retitlings, no data is touched. */
    @Test
    fun harmlessChangesAlone_requireNoMigration() {
        val changes = FieldConfigComparator.compare(
            oldFields = listOf(field("mood", displayName = "Mood")),
            newFields = listOf(field("mood", displayName = "How I felt"), field("sleep"))
        )

        val strategies = MigrationPolicy.getStrategies(changes)
        assertFalse(MigrationPolicy.requiresMigration(strategies))
        assertFalse(MigrationPolicy.hasErrorStrategy(strategies))
    }
}
