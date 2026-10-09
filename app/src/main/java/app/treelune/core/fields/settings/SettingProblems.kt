package app.treelune.core.fields.settings

import app.treelune.core.conditions.Conditions
import app.treelune.core.fields.FieldType
import app.treelune.core.fields.FilterOperator
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the form judges alone in an object of settings, without the service
 * (docs/design/settings-pages.md, « Les problèmes enfouis »): a required setting left empty, a
 * number out of its bounds, a list under its fewest elements, a condition or a term half written.
 * The line of a page says it, and so does each line above it up to the root, since a page holds the
 * pages under it. A refusal of the service's own (a chart's rule) stays its message on saving.
 */
object SettingProblems {

    /** Whether [config], the object [nodes] describe, holds a problem, in itself or in a page under it. */
    fun any(nodes: List<SettingNode>, config: JSONObject): Boolean = nodes.shown(config).any { node ->
        when (node) {
            is SettingNode.Field -> field(node, config)
            is SettingNode.Variant -> field(node.selector, config)
            is SettingNode.Group -> config.optJSONObject(node.name)?.let { any(node.nodes, it) } ?: node.required
            is SettingNode.ListOf -> list(node, config.optJSONArray(node.name) ?: JSONArray())
            // Put on the entry while its entered field is declared: whoever owns the key writes its left
            is SettingNode.Condition -> config.optJSONObject(node.name)?.let {
                condition(it, leftGiven = node.enteredField?.let { name -> config.optJSONObject(name) != null } == true)
            } ?: node.required
            is SettingNode.Term -> config.optJSONObject(node.name)?.let { !termWritten(it) } ?: node.required
            is SettingNode.Selection -> node.required && config.optJSONObject(node.name) == null
            is SettingNode.Period -> node.required && config.optJSONObject(node.name) == null
            is SettingNode.Section -> any(node.nodes, config)
        }
    }

    /** A value missing where it is required and has no default, or a number out of its bounds. */
    private fun field(node: SettingNode.Field, config: JSONObject): Boolean {
        if (node.systemWritten) return false
        val value = config.opt(node.definition.name)?.takeIf { it != JSONObject.NULL }
        val empty = value == null || (value is String && value.isBlank()) || (value is JSONArray && value.length() == 0)
        if (empty) return node.required && node.default == null
        if (node.definition.type != FieldType.NUMERIC || value !is Number) return false
        val bounds = node.definition.config ?: return false
        val min = (bounds["min"] as? Number)?.toDouble()
        val max = (bounds["max"] as? Number)?.toDouble()
        return (min != null && value.toDouble() < min) || (max != null && value.toDouble() > max)
    }

    /** Too few elements, or an element of groups holding a problem. */
    private fun list(node: SettingNode.ListOf, values: JSONArray): Boolean {
        if (values.length() < maxOf(node.minItems, if (node.required) 1 else 0)) return true
        val shape = node.item as? SettingNode.Item.Of ?: return false
        return (0 until values.length()).any { i -> values.optJSONObject(i)?.let { any(shape.nodes, it) } ?: true }
    }

    /**
     * A condition without its operator, or a side it needs left unwritten. [leftGiven]: its left is
     * the entry's field or the row's, which the form does not write on this side.
     */
    private fun condition(condition: JSONObject, leftGiven: Boolean): Boolean {
        val op = FilterOperator.of(condition.optString(Conditions.OP)) ?: return true
        if (!leftGiven && condition.optJSONObject(Conditions.LEFT)?.let { side(it) } != true) return true
        if (op == FilterOperator.ABSENT || op == FilterOperator.PRESENT) return false
        return when (val right = condition.opt(Conditions.RIGHT)) {
            is JSONObject -> !side(right)
            is JSONArray -> right.length() < 2 || (0 until right.length()).any { i -> right.optJSONObject(i)?.let { !side(it) } ?: true }
            else -> true
        }
    }

    /** One side of a condition: a field of the entry or the row, or a term. */
    private fun side(side: JSONObject): Boolean =
        side.optString(Conditions.FIELD).isNotEmpty() || termWritten(side)

    /** A term with what it needs: a written value, a variable's name, a reading. */
    private fun termWritten(term: JSONObject): Boolean = when {
        term.has(Conditions.CONSTANT) -> !term.isNull(Conditions.CONSTANT) && term.opt(Conditions.CONSTANT).toString().isNotBlank()
        term.has("variable") -> term.optString("variable").isNotEmpty()
        term.has("reading") -> term.optJSONObject("reading") != null
        else -> false
    }
}
