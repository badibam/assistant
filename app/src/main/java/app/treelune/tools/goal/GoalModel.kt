package app.treelune.tools.goal

import app.treelune.core.fields.FieldDefinition
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.toFieldConfig
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Whether a node is met: yes, no, or not known yet (a value missing, a reading that failed). */
enum class Met { YES, NO, UNKNOWN }

/**
 * One criterion: a condition (the Condition brick, docs/BRICKS.md) and, when it is entered, the
 * value it declares (docs/design/missing-tools.md, « Objectif »). A read criterion's condition is
 * judged once, its sides terms read at the end of the attempt; an entered one's is put on the
 * attempt, its left side the value entered, stored under [key].
 *
 * Its [key] is fixed when the criterion is created (GoalToolType.completeConfig): renaming it keeps
 * its values; a criterion deleted and made anew gets a new one.
 *
 * @property entered The type and settings of the value entered, `{"type", "config"}`, stored under "field"; null for a read criterion
 * @property condition Its stored form, `{"left", "op", "right"}`
 */
data class Criterion(
    val key: String,
    val name: String,
    val essential: Boolean,
    val entered: JSONObject?,
    val instructions: String?,
    val condition: JSONObject
) {
    /** The field an entered criterion's value is written in, in the attempt's data. */
    fun enteredField(): FieldDefinition? = entered?.let { declared ->
        FieldDefinition(
            name = key, displayName = name, description = instructions,
            type = FieldType.valueOf(declared.getString("type")), alwaysVisible = true,
            config = declared.optJSONObject("config")?.toFieldConfig()
        )
    }

    companion object {
        /** A name as a new field's key: lowercase ASCII words joined by `_`, "c_" first. */
        fun keyOf(name: String): String = "c_" + Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

        /** The path of the value an entered criterion of [key] writes in its attempt. */
        fun enteredPath(key: String) = "data.$key"

        /** @throws IllegalArgumentException on a criterion that has no key yet */
        fun fromJson(json: JSONObject) = Criterion(
            key = json.optString("key").takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("criterion \"${json.optString("name")}\" has no key"),
            name = json.getString("name"),
            essential = json.optBoolean("essential", false),
            entered = json.optJSONObject(ENTERED)?.takeIf { it.optString("type").isNotEmpty() },
            instructions = json.optString("instructions").takeIf { it.isNotEmpty() },
            condition = json.optJSONObject(CONDITION) ?: JSONObject()
        )

        const val ENTERED = "field"
        const val CONDITION = "condition"
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

    /** @param criteria Each criterion's value and whether it is met, by key; absent when unknown */
    fun judge(definition: GoalDefinition, criteria: Map<String, Pair<Any?, Met>>): Judgement {
        val criteria = definition.allCriteria.associate { it.key to (criteria[it.key] ?: (null to Met.UNKNOWN)) }
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
