package com.assistant.core.variables

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.reading.FailureReason
import com.assistant.core.reading.FieldReading
import com.assistant.core.reading.Reduction
import com.assistant.core.selection.EntrySelection
import com.assistant.core.selection.ReferenceKind

/** A variable as stored: its id, its name, what it is. */
data class StoredVariable(val id: String, val name: String, val zoneId: String, val definition: VariableDefinition)

/** What a variable gives at one instant: a value in the stored form of its field, or why not. */
sealed interface VariableValue {
    data class Value(val value: Any, val field: FieldDefinition) : VariableValue
    data class Failed(val causes: List<Cause>) : VariableValue
}

/** What the evaluator reads, from the database for the app, from memory for the tests. */
interface VariableSources {
    suspend fun variable(id: String): StoredVariable?

    /** A term's field reading at [at]: a number (a duration in milliseconds), or why not. */
    suspend fun read(term: Term.Reading, at: Long): Outcome

    /** The entries a term's selection gives at [at], newest first; null when its tool no longer exists. */
    suspend fun entries(selection: EntrySelection, at: Long): List<Map<String, Any?>>?

    /** One entry, for a reference read through; null when it no longer exists. */
    suspend fun entry(id: String): Map<String, Any?>?
}

/**
 * Computes a variable at an instant (docs/DATA.md, « Variables »): a constant is its value; a
 * formula reads its terms at that instant, each reading's period relative to it, and another
 * variable at the same instant. Nothing is stored, nothing is made up: a term that fails, a
 * division by zero, a variable deleted since or reached again fail the value, with the causes.
 */
class VariableEvaluator(private val sources: VariableSources) {

    suspend fun evaluate(variable: StoredVariable, at: Long): VariableValue = evaluate(variable, at, emptySet())

    private suspend fun evaluate(variable: StoredVariable, at: Long, visiting: Set<String>): VariableValue {
        val definition = variable.definition
        val outcome = when (definition) {
            is VariableDefinition.Constant -> Outcome.Value(definition.value)
            is VariableDefinition.Computed -> when (val parsed = Formula.parse(definition.formula)) {
                is Formula.Parsed.Unreadable -> Outcome.Failed(listOf(Cause(Cause.UNREADABLE, term = variable.name)))
                is Formula.Parsed.Read -> evaluateFormula(parsed.formula, definition.terms, at, visiting + variable.id)
            }
        }
        return when (outcome) {
            is Outcome.Failed -> VariableValue.Failed(outcome.causes)
            is Outcome.Value -> VariableValue.Value(stored(outcome.number, definition.field), definition.field)
        }
    }

    /** The formula computed, each leaf read as a suspending lookup first. */
    private suspend fun evaluateFormula(formula: Formula, terms: Map<String, Term>, at: Long, visiting: Set<String>): Outcome {
        val leaves = mutableMapOf<Formula, Outcome>()
        for (leaf in leavesOf(formula)) {
            leaves[leaf] = when (leaf) {
                is Formula.Name -> when (val term = terms[leaf.name]) {
                    null -> Outcome.Failed(listOf(Cause(Cause.UNKNOWN_NAME, term = leaf.name)))
                    is Term.Constant -> Outcome.Value(term.value)
                    is Term.Variable -> other(term.id, at, visiting, leaf.name)
                    is Term.Reading -> if (term.perEntry != null) perEntry(term, leaf.name, at) else named(sources.read(term, at), leaf.name)
                }
                is Formula.VariableRef -> other(leaf.id, at, visiting, null)
                else -> error("not a leaf")
            }
        }
        return formula.evaluate { leaves.getValue(it) }
    }

    private fun leavesOf(formula: Formula): Set<Formula> = when (formula) {
        is Formula.Name, is Formula.VariableRef -> setOf(formula)
        is Formula.Number -> emptySet()
        is Formula.Negate -> leavesOf(formula.operand)
        is Formula.Binary -> leavesOf(formula.left) + leavesOf(formula.right)
        is Formula.Call -> leavesOf(formula.argument)
    }

    /** Another variable at the same instant; its causes named by it. */
    private suspend fun other(id: String, at: Long, visiting: Set<String>, term: String?): Outcome {
        if (id in visiting) return Outcome.Failed(listOf(Cause(Cause.LOOP, term = term)))
        val variable = sources.variable(id) ?: return Outcome.Failed(listOf(Cause(Cause.VARIABLE_DELETED, term = term)))
        return when (val value = evaluate(variable, at, visiting)) {
            is VariableValue.Value -> Outcome.Value((value.value as Number).toDouble())
            is VariableValue.Failed -> Outcome.Failed(value.causes.map { it.copy(term = listOfNotNull(variable.name, it.term).joinToString(" › ")) })
        }
    }

    private fun named(outcome: Outcome, term: String): Outcome =
        if (outcome is Outcome.Failed) Outcome.Failed(outcome.causes.map { it.copy(term = it.term ?: term) }) else outcome

    /**
     * A formula computed in each selected entry, then reduced. A name reads a field of the entry
     * (its data, then its extra); `ref.field` reads through a REFERENCE to an entry, one step.
     */
    private suspend fun perEntry(term: Term.Reading, name: String, at: Long): Outcome {
        val formula = when (val parsed = Formula.parse(term.perEntry!!)) {
            is Formula.Parsed.Unreadable -> return Outcome.Failed(listOf(Cause(Cause.UNREADABLE, term = name)))
            is Formula.Parsed.Read -> parsed.formula
        }
        val entries = sources.entries(term.selection, at)
            ?: return Outcome.Failed(listOf(Cause(FailureReason.SOURCE_NOT_FOUND.name, term = name)))

        val numbers = mutableListOf<Double>()
        val causes = mutableListOf<Cause>()
        for (entry in entries) {
            val id = entry["id"] as String
            val leaves = mutableMapOf<Formula, Outcome>()
            for (leaf in leavesOf(formula)) leaves[leaf] = entryValue(entry, id, leaf, name)
            when (val outcome = formula.evaluate { leaves.getValue(it) }) {
                is Outcome.Value -> numbers.add(outcome.number)
                is Outcome.Failed -> causes.addAll(outcome.causes)
            }
        }
        if (causes.isNotEmpty()) {
            // One cause per reason and field, with every entry it concerns
            return Outcome.Failed(causes.groupBy { Triple(it.reason, it.term, it.field) }
                .map { (key, group) -> Cause(key.first, key.second, key.third, group.flatMap { it.entries }) })
        }
        if (numbers.isEmpty()) {
            return if (term.reduction == Reduction.SUM || term.reduction == Reduction.COUNT) Outcome.Value(0.0)
            else Outcome.Failed(listOf(Cause(FailureReason.NO_ENTRY.name, term = name)))
        }
        return Outcome.Value(when (term.reduction) {
            Reduction.SUM -> numbers.sum()
            Reduction.AVERAGE -> numbers.average()
            Reduction.MIN, Reduction.EARLIEST -> numbers.min()
            Reduction.MAX, Reduction.LATEST -> numbers.max()
            Reduction.LAST -> numbers.first()
            Reduction.COUNT -> numbers.size.toDouble()
        })
    }

    private suspend fun entryValue(entry: Map<String, Any?>, id: String, leaf: Formula, term: String): Outcome {
        val path = (leaf as? Formula.Name)?.name ?: return Outcome.Failed(listOf(Cause(Cause.UNKNOWN_NAME, term = term)))
        val value = if ('.' in path) {
            val reference = ReferenceTarget.referenceOf(fieldOf(entry, path.substringBefore('.')))
            val target = reference?.takeIf { it.kind == ReferenceKind.ENTRY }?.let { sources.entry(it.id!!) }
                ?: return Outcome.Failed(listOf(Cause(Cause.REFERENCE_BROKEN, term, path.substringBefore('.'), listOf(id))))
            fieldOf(target, path.substringAfter('.'))
        } else fieldOf(entry, path)
        return (value as? Number)?.let { Outcome.Value(it.toDouble()) }
            ?: Outcome.Failed(listOf(Cause(FailureReason.MISSING_VALUE.name, term, path, listOf(id))))
    }

    /** A key of an entry's data, else of its extra. */
    private fun fieldOf(entry: Map<String, Any?>, key: String): Any? =
        FieldReading.valueAt(entry, "data.$key") ?: FieldReading.valueAt(entry, "extra.$key")

    /** [number] in the stored form of [field]: whole milliseconds for a DURATION. */
    private fun stored(number: Double, field: FieldDefinition): Any =
        if (field.type == FieldType.DURATION) Math.round(number) else number
}
