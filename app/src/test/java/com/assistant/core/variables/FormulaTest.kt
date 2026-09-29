package com.assistant.core.variables

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A formula is read from its text, never run as code: the four operations with their usual
 * precedence, parentheses, names, a variable by its id, and the functions that turn a duration
 * into a number. A term that fails, or a division by zero, fails the whole, never gives 0.
 */
class FormulaTest {

    private fun read(text: String): Formula = (Formula.parse(text) as Formula.Parsed.Read).formula

    private fun unreadable(text: String): Formula.Parsed.Unreadable = Formula.parse(text) as Formula.Parsed.Unreadable

    private fun value(text: String, names: Map<String, Double> = emptyMap()): Double =
        (read(text).evaluate { leaf -> Outcome.Value(names.getValue((leaf as Formula.Name).name)) } as Outcome.Value).number

    @Test
    fun `products bind tighter than sums, parentheses first`() {
        assertEquals(7.0, value("1 + 2 * 3"), 0.0)
        assertEquals(9.0, value("(1 + 2) × 3"), 0.0)
        assertEquals(2.0, value("8 ÷ 2 / 2"), 0.0)
        assertEquals(-1.0, value("-(3 - 2)"), 0.0)
        assertEquals(5.0, value("10 - 2 - 3"), 0.0)
    }

    @Test
    fun `names with accents and dots read, and compute from what is given for them`() {
        assertEquals(listOf("quantité", "aliment.kcal_100g"), read("quantité × aliment.kcal_100g / 100").names())
        assertEquals(157.5, value("quantité × aliment.kcal_100g / 100", mapOf("quantité" to 150.0, "aliment.kcal_100g" to 105.0)), 1e-9)
    }

    @Test
    fun `a duration becomes a number of hours, minutes or seconds`() {
        assertEquals(10.0, value("km / heures(temps)", mapOf("km" to 15.0, "temps" to 5_400_000.0)), 1e-9)
        assertEquals(90.0, value("minutes(temps)", mapOf("temps" to 5_400_000.0)), 1e-9)
    }

    @Test
    fun `a variable is stored by its id and written back under its current name`() {
        val stored = read("mange - depense").mapNames { if (it == "depense") Formula.VariableRef("v42") else null }
        val text = stored.text { "{var:$it}" }
        assertEquals("mange - {var:v42}", text)
        assertEquals(listOf("v42"), read(text).variableIds())
        assertEquals("mange - sport", read(text).text { "sport" })
    }

    @Test
    fun `written back, the parentheses that matter stay and a function keeps its spelling`() {
        assertEquals("(mange - depense) ÷ 7", read("(mange-depense)/7").text { it })
        assertEquals("a - (b - c)", read("a - (b - c)").text { it })
        assertEquals("km ÷ hours(temps)", read("km / hours(temps)").text { it })
    }

    @Test
    fun `what does not read says where and why`() {
        assertEquals(Formula.Problem.EMPTY, unreadable("  ").problem)
        assertEquals(Formula.Problem.MISSING_CLOSING, unreadable("(a + b").problem)
        assertEquals(Formula.Problem.MISSING_OPERAND, unreadable("a + ").problem)
        assertEquals(Formula.Problem.UNKNOWN_FUNCTION, unreadable("racine(a)").problem)
        assertEquals(6, unreadable("a + b c").position)
    }

    @Test
    fun `a failing term fails the whole with the causes of both sides, and so does a division by zero`() {
        val failed = read("a + b").evaluate { leaf -> Outcome.Failed(listOf(Cause("NO_ENTRY", term = (leaf as Formula.Name).name))) }
        assertEquals(listOf("a", "b"), (failed as Outcome.Failed).causes.map { it.term })
        val byZero = read("a / 0").evaluate { Outcome.Value(1.0) } as Outcome.Failed
        assertEquals(Cause.DIVISION_BY_ZERO, byZero.causes.single().reason)
    }

    // Deduction of the value's type

    private fun field(type: FieldType, config: Map<String, Any>? = null) = FieldDefinition("f", "f", null, type, false, config)

    private val kcal = field(FieldType.NUMERIC, mapOf("decimals" to 0, "unit" to "kcal"))
    private val km = field(FieldType.NUMERIC, mapOf("decimals" to 1, "unit" to "km"))
    private val duration = field(FieldType.DURATION)

    private fun deduce(text: String, fields: Map<String, FieldDefinition>) =
        FormulaType.deduce(read(text)) { leaf -> fields[(leaf as Formula.Name).name] }

    @Test
    fun `an operation keeps what its sides share, a written number takes the other side's`() {
        assertEquals(FormulaType.Deduced(FieldType.NUMERIC, kcal.config), deduce("(mange - depense) / 7", mapOf("mange" to kcal, "depense" to kcal)))
        assertEquals(FormulaType.Deduced(FieldType.DURATION, null), deduce("temps * 2", mapOf("temps" to duration)))
    }

    @Test
    fun `anything else is a bare number`() {
        assertEquals(FormulaType.BARE, deduce("km / heures(temps)", mapOf("km" to km, "temps" to duration)))
        assertEquals(FormulaType.BARE, deduce("km + mange", mapOf("km" to km, "mange" to kcal)))
        assertTrue(deduce("3 + 4", emptyMap()) == FormulaType.BARE)
    }
}
