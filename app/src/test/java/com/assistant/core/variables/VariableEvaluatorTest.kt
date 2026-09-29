package com.assistant.core.variables

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.reading.Reduction
import com.assistant.core.selection.EntrySelection
import com.assistant.core.selection.Reference
import com.assistant.core.selection.ReferenceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A variable computed at an instant: its terms read at that instant, another variable read at the
 * same one, a formula per entry reading through a reference -- and every missing piece a failure
 * that names its term and its entries, never a 0 or a partial total.
 */
class VariableEvaluatorTest {

    private fun field(type: FieldType, config: Map<String, Any>? = mapOf("decimals" to 0)) = FieldDefinition("v", "v", null, type, false, config)

    private val meals = EntrySelection(Reference(ReferenceKind.TOOL_INSTANCE, "meals"))
    private val sport = EntrySelection(Reference(ReferenceKind.TOOL_INSTANCE, "sport"))

    /** Readings by tool, the instant they were asked at recorded. */
    private val readings = mutableMapOf<String, Outcome>()
    private val readAt = mutableListOf<Long>()
    private val variables = mutableMapOf<String, StoredVariable>()
    private val entries = mutableMapOf<String, List<Map<String, Any?>>>()
    private val sheets = mutableMapOf<String, Map<String, Any?>>()

    private val evaluator = VariableEvaluator(object : VariableSources {
        override suspend fun variable(id: String) = variables[id]
        override suspend fun read(term: Term.Reading, at: Long): Outcome {
            readAt.add(at)
            return readings.getValue(term.selection.target.id!!)
        }
        override suspend fun entries(selection: EntrySelection, at: Long) = entries[selection.target.id!!]
        override suspend fun entry(id: String) = sheets[id]
    })

    private fun formula(name: String, text: String, terms: Map<String, Term>, type: FieldType = FieldType.NUMERIC) =
        StoredVariable(name, name, "z", VariableDefinition.Computed(text, terms, field(type)))

    private fun value(variable: StoredVariable, at: Long = 1000L): Any =
        (runBlocking { evaluator.evaluate(variable, at) } as VariableValue.Value).value

    private fun causes(variable: StoredVariable): List<Cause> =
        (runBlocking { evaluator.evaluate(variable, 1000L) } as VariableValue.Failed).causes

    @Test
    fun `a constant is its value, a formula its terms read at the instant`() {
        assertEquals(2100.0, value(StoredVariable("goal", "goal", "z", VariableDefinition.Constant(2100.0, field(FieldType.NUMERIC)))))
        readings["meals"] = Outcome.Value(1800.0)
        readings["sport"] = Outcome.Value(300.0)
        val bilan = formula("bilan", "mange - depense", mapOf(
            "mange" to Term.Reading(meals, "data.kcal", Reduction.SUM),
            "depense" to Term.Reading(sport, "data.kcal", Reduction.SUM)
        ))
        assertEquals(1500.0, value(bilan, at = 42L))
        assertEquals(listOf(42L, 42L), readAt)
    }

    @Test
    fun `another variable is read at the same instant, by its id`() {
        readings["meals"] = Outcome.Value(1800.0)
        variables["mange"] = formula("mange", "total", mapOf("total" to Term.Reading(meals, "data.kcal", Reduction.SUM)))
        variables["goal"] = StoredVariable("goal", "goal", "z", VariableDefinition.Constant(2100.0, field(FieldType.NUMERIC)))
        assertEquals(300.0, value(formula("reste", "{var:goal} - {var:mange}", emptyMap())))
    }

    @Test
    fun `a duration stays whole milliseconds`() {
        readings["sport"] = Outcome.Value(5_400_001.0)
        assertEquals(2_700_001L, value(formula("half", "t / 2", mapOf("t" to Term.Reading(sport, "data.duration", Reduction.SUM)), FieldType.DURATION)))
    }

    @Test
    fun `a failing term fails the value, named by its term`() {
        readings["meals"] = Outcome.Failed(listOf(Cause("NO_ENTRY", field = "data.kcal")))
        readings["sport"] = Outcome.Value(300.0)
        val bilan = formula("bilan", "mange - depense", mapOf(
            "mange" to Term.Reading(meals, "data.kcal", Reduction.LAST),
            "depense" to Term.Reading(sport, "data.kcal", Reduction.SUM)
        ))
        assertEquals(listOf(Cause("NO_ENTRY", term = "mange", field = "data.kcal")), causes(bilan))
    }

    @Test
    fun `a variable deleted or reached again fails, never loops`() {
        assertEquals(Cause.VARIABLE_DELETED, causes(formula("x", "{var:gone} + 1", emptyMap())).single().reason)
        variables["a"] = formula("a", "{var:b}", emptyMap())
        variables["b"] = formula("b", "{var:a}", emptyMap())
        assertEquals(Cause.LOOP, causes(variables.getValue("a")).single().reason)
    }

    @Test
    fun `a formula per entry reads through a reference and sums`() {
        sheets["apple"] = mapOf("id" to "apple", "data" to emptyMap<String, Any?>(), "extra" to mapOf("kcal_100g" to 52))
        sheets["bread"] = mapOf("id" to "bread", "data" to emptyMap<String, Any?>(), "extra" to mapOf("kcal_100g" to 250))
        entries["meals"] = listOf(
            mapOf("id" to "m1", "data" to mapOf("quantity" to 200), "extra" to mapOf("food" to mapOf("kind" to "ENTRY", "id" to "apple"))),
            mapOf("id" to "m2", "data" to mapOf("quantity" to 100), "extra" to mapOf("food" to mapOf("kind" to "ENTRY", "id" to "bread")))
        )
        val kcal = formula("kcal", "mange", mapOf("mange" to Term.Reading(meals, null, Reduction.SUM, perEntry = "quantity × food.kcal_100g / 100")))
        assertEquals(354.0, value(kcal))
    }

    @Test
    fun `an entry without its food or its quantity fails the sum, naming the entries`() {
        entries["meals"] = listOf(
            mapOf("id" to "m1", "data" to mapOf("quantity" to 200), "extra" to mapOf("food" to mapOf("kind" to "ENTRY", "id" to "gone"))),
            mapOf("id" to "m2", "data" to emptyMap<String, Any?>(), "extra" to null),
            mapOf("id" to "m3", "data" to emptyMap<String, Any?>(), "extra" to null)
        )
        val kcal = formula("kcal", "mange", mapOf("mange" to Term.Reading(meals, null, Reduction.SUM, perEntry = "quantity × food.kcal_100g / 100")))
        val found = causes(kcal).associateBy { it.reason }
        assertEquals(listOf("m1", "m2", "m3"), found.getValue(Cause.REFERENCE_BROKEN).entries)
        assertEquals(listOf("m2", "m3"), found.getValue("MISSING_VALUE").entries)
    }

    @Test
    fun `no meal is a sum of zero, not a failure`() {
        entries["meals"] = emptyList()
        assertEquals(0.0, value(formula("kcal", "mange", mapOf("mange" to Term.Reading(meals, null, Reduction.SUM, perEntry = "quantity")))))
    }
}
