package com.assistant.core.ui.selectors

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldInput
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.ReferenceTarget
import com.assistant.core.reading.Reduction
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.strings.StringsContext
import com.assistant.core.terms.Term
import com.assistant.core.ui.UI
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * The Terme brick's selector (docs/BRICKS.md): a constant, a variable, or a reading, [term] in its
 * stored form (Term). The constant is entered as [constantField], the type the context reads it as
 * (a number in a formula, the field on the other side of a condition); a variable is designated
 * as a thing, reached in its zone.
 *
 * @param kinds The kinds the context takes (a chart's grid reads, and has no constant); a term
 *   of none yet starts as the first of them
 */
@Composable
fun TermPicker(term: JSONObject, constantField: FieldDefinition, onChange: (JSONObject) -> Unit, s: StringsContext, where: ReadingContext,
               kinds: Set<Term.Kind> = Term.Kind.entries.toSet()) {
    val context = LocalContext.current
    val offered = listOf(READING to s.shared("variable_term_reading"), CONSTANT to s.shared("variable_term_constant"), VARIABLE to s.shared("variable_term_variable"))
        .filter { (key, _) -> kinds.any { it.key == key } }
    val kind = offered.firstOrNull { term.has(it.first) }?.first ?: if (kinds.contains(Term.Kind.CONSTANT)) CONSTANT else offered.first().first
    if (offered.size > 1) UI.FormSelection(label = s.shared("variable_term_kind"), options = offered.map { it.second }, selected = offered.first { it.first == kind }.second, onSelect = { label ->
        onChange(fresh(offered.first { it.second == label }.first))
    }, required = true)
    // A term of none of the kinds offered yet shows as the one it starts as, written once chosen
    val shown = if (term.has(kind)) term else fresh(kind)
    when (kind) {
        CONSTANT -> FieldInput(
            constantField.copy(displayName = s.shared("variable_value")),
            JsonUtils.toValue(shown.opt(CONSTANT)?.takeIf { it != JSONObject.NULL }),
            { value -> onChange(if (value == null) JSONObject().put(CONSTANT, JSONObject.NULL) else Term.Constant(value).toJson()) },
            context, required = true
        )
        VARIABLE -> FieldInput(
            FieldDefinition(VARIABLE, s.shared("variable_term_variable"), null, FieldType.REFERENCE, false,
                mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.VARIABLE.name)))),
            shown.optString(VARIABLE).takeIf { it.isNotEmpty() }?.let { mapOf("kind" to ReferenceKind.VARIABLE.name, "id" to it) },
            { value -> onChange(JSONObject().put(VARIABLE, ReferenceTarget.referenceOf(value)?.id ?: "")) },
            context, required = true
        )
        else -> ReadingPicker(shown.getJSONObject(READING), s, where) { onChange(JSONObject().put(READING, it)) }
    }
}

/** A term of [kind] with nothing chosen yet. */
private fun fresh(kind: String): JSONObject = when (kind) {
    READING -> JSONObject().put(READING, JSONObject().put("selection", JSONObject()).put("reduction", Reduction.COUNT.name))
    VARIABLE -> JSONObject().put(VARIABLE, "")
    else -> JSONObject().put(CONSTANT, JSONObject.NULL)
}

private const val READING = "reading"
private const val CONSTANT = "constant"
private const val VARIABLE = "variable"
