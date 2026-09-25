package com.assistant.core.fields.migration

import com.assistant.core.fields.ChoiceShape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.assistant.core.fields.FieldType
import org.junit.Test

/**
 * Covers what a configuration change does to the entries already recorded.
 *
 * The strategies say what should happen; these cases say what happens. The two had drifted: the
 * confirmation dialog told the user a scale's data would be erased, and the transformation left
 * it in place.
 *
 * Nothing here needs a Context or a database: the transformation is a function of the map, the
 * changes and the strategies.
 */
class FieldDataMigratorTest {

    private fun migrate(
        customFields: Map<String, Any?>,
        vararg changes: Pair<FieldChange, MigrationStrategy>
    ): Map<String, Any?> = FieldDataMigrator.applyMigrationStrategies(
        customFields = customFields,
        changes = changes.map { it.first },
        strategies = changes.toMap()
    )

    /** A deleted field leaves the entries with it. */
    @Test
    fun aRemovedFieldLeavesTheEntry() {
        val result = migrate(
            mapOf("mood" to "good", "weight" to 75),
            FieldChange.Removed("mood") to MigrationStrategy.STRIP_FIELD
        )

        assertFalse(result.containsKey("mood"))
        assertEquals(75, result["weight"])
    }

    /**
     * A scale's range is its unit: a 3 out of 10 is not a 3 out of 5, so every value goes, and
     * not only the ones outside the new range. This is what the dialog has always announced.
     */
    @Test
    fun aScaleWhoseRangeChangedLosesEveryValue() {
        val result = migrate(
            mapOf("energy" to 3),
            FieldChange.ScaleRangeChanged("energy", 0, 10, 0, 5) to MigrationStrategy.STRIP_FIELD
        )

        assertFalse("a value inside the new range goes too", result.containsKey("energy"))
    }

    /** One value or a list of them are different shapes, so what was stored cannot be read. */
    @Test
    fun aChoiceThatChangedBetweenOneAndSeveralLosesItsValue() {
        val result = migrate(
            mapOf("tags" to "urgent"),
            FieldChange.ChoiceShapeChanged("tags", ChoiceShape.SINGLE, ChoiceShape.MULTIPLE) to MigrationStrategy.STRIP_FIELD
        )

        assertFalse(result.containsKey("tags"))
    }

    /** A removed option only concerns the entries that used it. */
    @Test
    fun aRemovedOptionOnlyTouchesTheEntriesUsingIt() {
        val change = FieldChange.ChoiceOptionsRemoved("status", listOf("draft"))

        val usingIt = migrate(
            mapOf("status" to "draft"),
            change to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )
        val notUsingIt = migrate(
            mapOf("status" to "sent"),
            change to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )

        assertFalse(usingIt.containsKey("status"))
        assertEquals("sent", notUsingIt["status"])
    }

    /** In a multiple choice, one removed option among several is enough. */
    @Test
    fun aRemovedOptionInsideAListIsEnough() {
        val result = migrate(
            mapOf("tags" to listOf("urgent", "draft")),
            FieldChange.ChoiceOptionsRemoved("tags", listOf("draft")) to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )

        assertFalse(result.containsKey("tags"))
    }

    /** Renaming what a field is called on screen touches no data. */
    @Test
    fun aCosmeticChangeLeavesEverything() {
        val result = migrate(
            mapOf("mood" to "good"),
            FieldChange.CosmeticChange("mood") to MigrationStrategy.NONE
        )

        assertEquals("good", result["mood"])
    }

    /** An entry that never held the changed field comes out as it went in. */
    @Test
    fun anEntryWithoutTheFieldIsUntouched() {
        val result = migrate(
            mapOf("weight" to 75),
            FieldChange.ScaleRangeChanged("energy", 0, 10, 0, 5) to MigrationStrategy.STRIP_FIELD
        )

        assertEquals(1, result.size)
        assertTrue(result.containsKey("weight"))
    }

    // ==================== A config that restricts what is allowed ====================

    /** A number below the new floor no longer fits, so it goes. */
    @Test
    fun aNumberOutsideTheNewBoundsGoes() {
        val result = migrate(
            mapOf("weight" to 5),
            FieldChange.ConfigRestricted("weight", FieldType.NUMERIC, mapOf("min" to 10, "max" to 300))
                to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )

        assertFalse(result.containsKey("weight"))
    }

    /** A number still inside them keeps its meaning and stays: 75 kg is 75 kg either way. */
    @Test
    fun aNumberInsideTheNewBoundsStays() {
        val result = migrate(
            mapOf("weight" to 75),
            FieldChange.ConfigRestricted("weight", FieldType.NUMERIC, mapOf("min" to 10, "max" to 300))
                to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )

        assertEquals(75, result["weight"])
    }

    /** Widening a bound reaches here as well, and takes nothing. */
    @Test
    fun awidenedBoundTakesNothing() {
        val result = migrate(
            mapOf("weight" to 5),
            FieldChange.ConfigRestricted("weight", FieldType.NUMERIC, mapOf("min" to 0, "max" to 500))
                to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )

        assertEquals(5, result["weight"])
    }

    /** A value carrying more decimals than the config now allows cannot be written back. */
    @Test
    fun aNumberWithTooManyDecimalsGoes() {
        val result = migrate(
            mapOf("dose" to 2.75),
            FieldChange.ConfigRestricted("dose", FieldType.NUMERIC, mapOf("decimals" to 1))
                to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )

        assertFalse(result.containsKey("dose"))
    }

    /** A whole number passes a config that allows no decimals. */
    @Test
    fun awholeNumberPassesAConfigWithoutDecimals() {
        val result = migrate(
            mapOf("dose" to 3.0),
            FieldChange.ConfigRestricted("dose", FieldType.NUMERIC, mapOf("decimals" to 0))
                to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )

        assertEquals(3.0, result["dose"])
    }

    /** A text longer than the new limit goes; the shorter one beside it stays. */
    @Test
    fun aTextLongerThanTheNewLimitGoes() {
        val change = FieldChange.ConfigRestricted("note", FieldType.TEXT, mapOf("length" to "SHORT"))

        val long = migrate(
            mapOf("note" to "x".repeat(200)),
            change to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )
        val short = migrate(
            mapOf("note" to "court"),
            change to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )

        assertFalse(long.containsKey("note"))
        assertEquals("court", short["note"])
    }

    /** An unlimited length takes nothing, whatever was written. */
    @Test
    fun anUnlimitedLengthTakesNothing() {
        val result = migrate(
            mapOf("note" to "x".repeat(5000)),
            FieldChange.ConfigRestricted("note", FieldType.TEXT, mapOf("length" to "UNLIMITED"))
                to MigrationStrategy.STRIP_FIELD_IF_VALUE
        )

        assertTrue(result.containsKey("note"))
    }
}
