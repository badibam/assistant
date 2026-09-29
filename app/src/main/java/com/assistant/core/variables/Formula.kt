package com.assistant.core.variables

/**
 * A formula as its text reads: numbers, names, `+ - × ÷` (`*` and `/` too), parentheses, and the
 * functions of [Function]. The app reads it into this tree and computes it; it never runs it as
 * code (docs/DATA.md, « Variables »).
 *
 * A name is a word of letters, digits and `_`, accents included (`quantité`), possibly dotted to
 * read through a reference (`aliment.kcal_100g`). A variable named in a stored formula is written
 * `{var:ID}`, which the text shows under its current name.
 */
sealed interface Formula {
    data class Number(val value: Double) : Formula
    data class Name(val name: String) : Formula
    data class VariableRef(val id: String) : Formula
    data class Negate(val operand: Formula) : Formula
    data class Binary(val op: Operator, val left: Formula, val right: Formula) : Formula
    /** [spelled] is the function's name as written, kept when the text is written back. */
    data class Call(val function: Function, val argument: Formula, val spelled: String) : Formula

    enum class Operator(val symbol: String) { PLUS("+"), MINUS("-"), TIMES("×"), DIVIDE("÷") }

    /** A function, added for a real case only: the first ones turn a duration into a number of units. */
    enum class Function(val millis: Double) {
        HOURS(3_600_000.0), MINUTES(60_000.0), SECONDS(1_000.0);

        companion object {
            /** The names a formula calls them by, in either language. */
            val byName: Map<String, Function> = mapOf(
                "heures" to HOURS, "hours" to HOURS,
                "minutes" to MINUTES,
                "secondes" to SECONDS, "seconds" to SECONDS
            )
        }
    }

    /**
     * The formula's value, [leaf] giving each name's and each variable's. A leaf that fails, or a
     * division by zero, fails the whole with its causes, those of both sides when both fail: never
     * 0, never a partial result.
     */
    fun evaluate(leaf: (Formula) -> Outcome): Outcome = when (this) {
        is Number -> Outcome.Value(value)
        is Name, is VariableRef -> leaf(this)
        is Negate -> when (val o = operand.evaluate(leaf)) { is Outcome.Value -> Outcome.Value(-o.number); is Outcome.Failed -> o }
        is Call -> when (val o = argument.evaluate(leaf)) { is Outcome.Value -> Outcome.Value(o.number / function.millis); is Outcome.Failed -> o }
        is Binary -> {
            val l = left.evaluate(leaf)
            val r = right.evaluate(leaf)
            when {
                l is Outcome.Failed || r is Outcome.Failed ->
                    Outcome.Failed((l as? Outcome.Failed)?.causes.orEmpty() + (r as? Outcome.Failed)?.causes.orEmpty())
                else -> {
                    val a = (l as Outcome.Value).number
                    val b = (r as Outcome.Value).number
                    when (op) {
                        Operator.PLUS -> Outcome.Value(a + b)
                        Operator.MINUS -> Outcome.Value(a - b)
                        Operator.TIMES -> Outcome.Value(a * b)
                        Operator.DIVIDE -> if (b == 0.0) Outcome.Failed(listOf(Cause(Cause.DIVISION_BY_ZERO))) else Outcome.Value(a / b)
                    }
                }
            }
        }
    }

    /** Every name the formula reads, in order of appearance. */
    fun names(): List<String> = when (this) {
        is Number, is VariableRef -> emptyList()
        is Name -> listOf(name)
        is Negate -> operand.names()
        is Binary -> left.names() + right.names()
        is Call -> argument.names()
    }

    /** Every variable the formula reads by id. */
    fun variableIds(): List<String> = when (this) {
        is Number, is Name -> emptyList()
        is VariableRef -> listOf(id)
        is Negate -> operand.variableIds()
        is Binary -> left.variableIds() + right.variableIds()
        is Call -> argument.variableIds()
    }

    /** The tree with each name [replace] gives a node for replaced by it: a variable's name by its id. */
    fun mapNames(replace: (String) -> Formula?): Formula = when (this) {
        is Number, is VariableRef -> this
        is Name -> replace(name) ?: this
        is Negate -> Negate(operand.mapNames(replace))
        is Binary -> Binary(op, left.mapNames(replace), right.mapNames(replace))
        is Call -> Call(function, argument.mapNames(replace), spelled)
    }

    /** The formula written back as text, a variable by [variableName]. */
    fun text(variableName: (String) -> String): String = write(this, variableName, 0)

    companion object {
        /** Reads [text], or says where it does not read. */
        fun parse(text: String): Parsed = Parser(text).parse()

        private fun write(node: Formula, variableName: (String) -> String, parent: Int): String = when (node) {
            is Number -> if (node.value % 1.0 == 0.0 && kotlin.math.abs(node.value) < 1e15) node.value.toLong().toString() else node.value.toString()
            is Name -> node.name
            is VariableRef -> variableName(node.id)
            is Negate -> "-" + write(node.operand, variableName, 3)
            is Call -> node.spelled + "(" + write(node.argument, variableName, 0) + ")"
            is Binary -> {
                val level = if (node.op == Operator.PLUS || node.op == Operator.MINUS) 1 else 2
                // The right side of - and ÷ keeps its parentheses at the same level: a - (b - c)
                val right = write(node.right, variableName, if (node.op == Operator.MINUS || node.op == Operator.DIVIDE) level + 1 else level)
                val inner = write(node.left, variableName, level) + " ${node.op.symbol} " + right
                if (level < parent) "($inner)" else inner
            }
        }
    }

    /** A formula read, or where and why it does not read. */
    sealed interface Parsed {
        data class Read(val formula: Formula) : Parsed
        data class Unreadable(val position: Int, val problem: Problem, val detail: String = "") : Parsed
    }

    /** What stops a text from reading. */
    enum class Problem { EMPTY, UNEXPECTED, MISSING_CLOSING, MISSING_OPERAND, UNKNOWN_FUNCTION, BAD_NUMBER }
}

/** A recursive descent over the text: sums of products of signed factors. */
private class Parser(private val text: String) {
    private var at = 0

    private class Stop(val position: Int, val problem: Formula.Problem, val detail: String = "") : Exception()

    fun parse(): Formula.Parsed = try {
        skipSpaces()
        if (at >= text.length) throw Stop(0, Formula.Problem.EMPTY)
        val formula = sum()
        skipSpaces()
        if (at < text.length) throw Stop(at, Formula.Problem.UNEXPECTED, text[at].toString())
        Formula.Parsed.Read(formula)
    } catch (stop: Stop) {
        Formula.Parsed.Unreadable(stop.position, stop.problem, stop.detail)
    }

    private fun skipSpaces() { while (at < text.length && text[at].isWhitespace()) at++ }

    private fun peek(): Char? { skipSpaces(); return text.getOrNull(at) }

    private fun sum(): Formula {
        var left = product()
        while (true) {
            val op = when (peek()) { '+' -> Formula.Operator.PLUS; '-', '−' -> Formula.Operator.MINUS; else -> return left }
            at++
            left = Formula.Binary(op, left, product())
        }
    }

    private fun product(): Formula {
        var left = factor()
        while (true) {
            val op = when (peek()) { '*', '×' -> Formula.Operator.TIMES; '/', '÷' -> Formula.Operator.DIVIDE; else -> return left }
            at++
            left = Formula.Binary(op, left, factor())
        }
    }

    private fun factor(): Formula {
        val c = peek() ?: throw Stop(at, Formula.Problem.MISSING_OPERAND)
        return when {
            c == '-' || c == '−' -> { at++; Formula.Negate(factor()) }
            c == '(' -> {
                val open = at
                at++
                val inner = sum()
                if (peek() != ')') throw Stop(open, Formula.Problem.MISSING_CLOSING)
                at++
                inner
            }
            c == '{' -> variableRef()
            c.isDigit() || c == '.' -> number()
            c.isLetter() || c == '_' -> nameOrCall()
            else -> throw Stop(at, if (c == ')' || c in "+*/×÷") Formula.Problem.MISSING_OPERAND else Formula.Problem.UNEXPECTED, c.toString())
        }
    }

    private fun number(): Formula {
        val start = at
        while (at < text.length && (text[at].isDigit() || text[at] == '.')) at++
        val value = text.substring(start, at).toDoubleOrNull() ?: throw Stop(start, Formula.Problem.BAD_NUMBER, text.substring(start, at))
        return Formula.Number(value)
    }

    private fun nameOrCall(): Formula {
        val start = at
        while (at < text.length && (text[at].isLetterOrDigit() || text[at] == '_' ||
                (text[at] == '.' && text.getOrNull(at + 1)?.let { it.isLetter() || it == '_' } == true))) at++
        val name = text.substring(start, at)
        if (peek() != '(') return Formula.Name(name)
        val function = Formula.Function.byName[name.lowercase()] ?: throw Stop(start, Formula.Problem.UNKNOWN_FUNCTION, name)
        val open = at
        at++
        val argument = sum()
        if (peek() != ')') throw Stop(open, Formula.Problem.MISSING_CLOSING)
        at++
        return Formula.Call(function, argument, name)
    }

    private fun variableRef(): Formula {
        val start = at
        val end = text.indexOf('}', start)
        val inside = if (end < 0) "" else text.substring(start + 1, end)
        if (!inside.startsWith("var:") || inside.length <= 4) throw Stop(start, Formula.Problem.UNEXPECTED, "{")
        at = end + 1
        return Formula.VariableRef(inside.removePrefix("var:"))
    }
}

/** What a formula or one of its terms gives: a number (a duration in milliseconds), or why not. */
sealed interface Outcome {
    data class Value(val number: Double) : Outcome
    data class Failed(val causes: List<Cause>) : Outcome
}

/**
 * Why a value is missing, as data: the reason (a reading's FailureReason, or one of the
 * formula's own), the term or variable it comes from, the field, the entries to correct.
 */
data class Cause(val reason: String, val term: String? = null, val field: String? = null, val entries: List<String> = emptyList()) {
    companion object {
        const val DIVISION_BY_ZERO = "DIVISION_BY_ZERO"
        const val VARIABLE_DELETED = "VARIABLE_DELETED"
        const val REFERENCE_BROKEN = "REFERENCE_BROKEN"
        const val LOOP = "LOOP"
    }
}
