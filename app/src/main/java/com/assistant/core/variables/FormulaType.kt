package com.assistant.core.variables

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType

/**
 * The type and settings a formula's value takes, deduced from what it reads, never to refuse
 * (docs/DATA.md, « Variables »): a reduction keeps its source's, an operation the ones its two
 * sides share (km + km, a duration × 2), a number written in the formula taking the other side's
 * (`(mange - depense) / 7` stays in kcal), and anything else is a bare number (`km / heures(temps)`).
 * The user completes what this does not give (the unit "km/h") and corrects a setting.
 */
object FormulaType {

    /** A bare number: a NUMERIC with two decimals and no unit. */
    val BARE = Deduced(FieldType.NUMERIC, mapOf("decimals" to 2))

    /** A type and its settings, as a field carries them. */
    data class Deduced(val type: FieldType, val config: Map<String, Any>?)

    /**
     * @param leaf The field of a name or a variable the formula reads, as its term or variable
     *   gives it; null when unknown, which makes the whole a bare number
     */
    fun deduce(formula: Formula, leaf: (Formula) -> FieldDefinition?): Deduced = shape(formula, leaf) ?: BARE

    /** Null for a number written in the formula, which takes the type of what it meets. */
    private fun shape(node: Formula, leaf: (Formula) -> FieldDefinition?): Deduced? = when (node) {
        is Formula.Number -> null
        is Formula.Name, is Formula.VariableRef -> leaf(node)?.let { Deduced(it.type, it.config) } ?: BARE
        is Formula.Negate -> shape(node.operand, leaf)
        is Formula.Call -> BARE
        is Formula.Binary -> {
            val left = shape(node.left, leaf)
            val right = shape(node.right, leaf)
            when {
                left == null -> right
                right == null -> left
                node.op == Formula.Operator.PLUS || node.op == Formula.Operator.MINUS -> if (left == right) left else BARE
                // A duration by a duration is a ratio; anything else multiplied by its own kind has no field type
                else -> BARE
            }
        }
    }
}
