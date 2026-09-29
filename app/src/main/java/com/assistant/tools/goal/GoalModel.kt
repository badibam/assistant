package com.assistant.tools.goal

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.reading.Reduction
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Whether a node is met: yes, no, or not known yet (a value missing, a reading that failed). */
enum class Met { YES, NO, UNKNOWN }

/** Where a criterion's value comes from, and what kind of value it is. */
enum class CriterionKind(val entered: FieldType?) {
    /** A variable of the core, read at the end of the attempt's period */
    VARIABLE(null),
    /** A field of a tool reduced over the attempt's period */
    FIELD(null),
    /** Entered in the attempt, by the user or the AI */
    ENTERED_BOOLEAN(FieldType.BOOLEAN),
    ENTERED_NUMBER(FieldType.NUMERIC),
    ENTERED_DURATION(FieldType.DURATION),
    ENTERED_SCALE(FieldType.SCALE)
}

/** How a read value is compared with a number written in the condition, when it is a duration. */
enum class TargetUnit(val millis: Double) { NUMBER(1.0), MINUTES(60_000.0), HOURS(3_600_000.0) }

/**
 * One criterion: a value and a condition on it (docs/design/missing-tools.md, « Objectif »). Its
 * [key] is the field an entered value is stored under, fixed when the criterion is created
 * (GoalToolType.completeConfig): renaming it keeps its values; a criterion deleted and made anew
 * gets a new one.
 */
data class Criterion(
    val key: String,
    val name: String,
    val kind: CriterionKind,
    val essential: Boolean,
    val op: String,
    val target: Any?,
    val targetUnit: TargetUnit = TargetUnit.NUMBER,
    val variable: String? = null,
    val tool: String? = null,
    val field: String? = null,
    val reduction: Reduction? = null,
    val unit: String? = null,
    val instructions: String? = null
) {
    /** The field an entered criterion's value is written in. */
    fun enteredField(): FieldDefinition? = kind.entered?.let { type ->
        FieldDefinition(
            name = key, displayName = name, description = instructions, type = type, alwaysVisible = true,
            config = when (type) {
                FieldType.NUMERIC -> mapOf("decimals" to 2) + (unit?.let { mapOf("unit" to it) } ?: emptyMap())
                FieldType.SCALE -> mapOf("min" to 1, "max" to 10)
                else -> null
            }
        )
    }

    /** Whether [value] meets the condition; unknown without a value. */
    fun meets(value: Any?): Met {
        if (value == null) return Met.UNKNOWN
        if (kind == CriterionKind.ENTERED_BOOLEAN || value is Boolean) {
            return if (value == (target as? Boolean ?: true)) Met.YES else Met.NO
        }
        val number = (value as? Number)?.toDouble() ?: return Met.UNKNOWN
        val goal = (target as? Number)?.toDouble() ?: return Met.UNKNOWN
        // A duration entered is compared in milliseconds; one read, in the unit the condition says
        val compared = if (kind == CriterionKind.ENTERED_DURATION) goal else goal * targetUnit.millis
        val holds = when (op) {
            "<" -> number < compared
            "<=" -> number <= compared
            "=" -> number == compared
            ">=" -> number >= compared
            ">" -> number > compared
            else -> return Met.UNKNOWN
        }
        return if (holds) Met.YES else Met.NO
    }

    companion object {
        /** A name as a new field's key: lowercase ASCII words joined by `_`, "c_" first. */
        fun keyOf(name: String): String = "c_" + Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

        /** @throws IllegalArgumentException on a criterion that has no key yet */
        fun fromJson(json: JSONObject) = Criterion(
            key = json.optString("key").takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("criterion \"${json.optString("name")}\" has no key"),
            name = json.getString("name"),
            kind = CriterionKind.valueOf(json.getString("kind")),
            essential = json.optBoolean("essential", false),
            op = json.optString("op", "="),
            target = json.opt("target")?.takeIf { it != JSONObject.NULL },
            targetUnit = json.optString("target_unit").takeIf { it.isNotEmpty() }?.let { TargetUnit.valueOf(it) } ?: TargetUnit.NUMBER,
            variable = json.optString("variable").takeIf { it.isNotEmpty() },
            tool = json.opt("tool")?.let { ReferenceTarget.referenceOf(it)?.id ?: it as? String },
            field = json.optString("field").takeIf { it.isNotEmpty() },
            reduction = json.optString("reduction").takeIf { it.isNotEmpty() }?.let { Reduction.valueOf(it) },
            unit = json.optString("unit").takeIf { it.isNotEmpty() },
            instructions = json.optString("instructions").takeIf { it.isNotEmpty() }
        )
    }
}

/** A group of criteria within the goal, one level: at least [atLeast] of them, [essential] for the goal. */
data class SubGoal(val name: String, val criteria: List<Criterion>, val atLeast: Int?, val essential: Boolean)

/**
 * What a goal asks, as its config and each attempt's copy hold it: criteria of its own and
 * sub-goals, at least [atLeast] of them met, all by default. No weight, no percentage.
 */
data class GoalDefinition(val criteria: List<Criterion>, val subGoals: List<SubGoal>, val atLeast: Int?) {

    val allCriteria: List<Criterion> get() = criteria + subGoals.flatMap { it.criteria }

    companion object {
        /** The definition in [config] (or in an attempt's copy, the same keys). */
        fun of(config: JSONObject): GoalDefinition = GoalDefinition(
            criteria = list(config.optJSONArray("criteria")).map { Criterion.fromJson(it) },
            subGoals = list(config.optJSONArray("sub_goals")).map { sub ->
                SubGoal(
                    name = sub.getString("name"),
                    criteria = list(sub.optJSONArray("criteria")).map { Criterion.fromJson(it) },
                    atLeast = (sub.opt("at_least") as? Number)?.toInt(),
                    essential = sub.optBoolean("essential", false)
                )
            },
            atLeast = (config.opt("at_least") as? Number)?.toInt()
        )

        /** The part of [config] a definition is, as an attempt copies it. */
        fun copyOf(config: JSONObject): JSONObject = JSONObject().apply {
            config.optJSONArray("criteria")?.let { put("criteria", it) }
            config.optJSONArray("sub_goals")?.let { put("sub_goals", it) }
            config.opt("at_least")?.takeIf { it != JSONObject.NULL }?.let { put("at_least", it) }
        }

        private fun list(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else (0 until array.length()).map { array.getJSONObject(it) }
    }
}

/** The judgement of an attempt: each criterion's value and whether it is met (by key), each sub-goal's, the goal's. */
data class Judgement(
    val criteria: Map<String, Pair<Any?, Met>>,
    val subGoals: Map<String, Met>,
    val verdict: Met,
    val met: Int,
    val required: Int
)

/**
 * A goal judged by counting (docs/design/missing-tools.md, « Objectif »): every node is met or
 * not. A node with children asks at least N of them, all by default, and each essential one: an
 * essential child not met fails its parent, and it alone. A node with children still unknown is
 * unknown until what is known settles it.
 */
object GoalJudge {

    /** @param values Each criterion's value by key: entered, or read; absent when unknown */
    fun judge(definition: GoalDefinition, values: Map<String, Any?>): Judgement {
        val criteria = definition.allCriteria.associate { it.key to (values[it.key] to it.meets(values[it.key])) }
        val subGoals = definition.subGoals.associate { sub ->
            sub.name to node(sub.criteria.map { criteria.getValue(it.key).second to it.essential }, sub.atLeast)
        }
        val children = definition.criteria.map { criteria.getValue(it.key).second to it.essential } +
            definition.subGoals.map { subGoals.getValue(it.name) to it.essential }
        return Judgement(
            criteria = criteria,
            subGoals = subGoals,
            verdict = node(children, definition.atLeast),
            met = children.count { it.first == Met.YES },
            required = (definition.atLeast ?: children.size).coerceAtMost(children.size)
        )
    }

    /** A node from its children, each met or not and essential or not. */
    fun node(children: List<Pair<Met, Boolean>>, atLeast: Int?): Met {
        val needed = (atLeast ?: children.size).coerceAtMost(children.size)
        if (children.any { (met, essential) -> essential && met == Met.NO }) return Met.NO
        val yes = children.count { it.first == Met.YES }
        val unknown = children.count { it.first == Met.UNKNOWN }
        val essentialsUnknown = children.any { (met, essential) -> essential && met == Met.UNKNOWN }
        return when {
            yes >= needed && !essentialsUnknown -> Met.YES
            yes + unknown < needed -> Met.NO
            else -> Met.UNKNOWN
        }
    }
}
