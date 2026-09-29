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
 */
@Composable
fun TermPicker(term: JSONObject, constantField: FieldDefinition, onChange: (JSONObject) -> Unit, s: StringsContext) {
    val context = LocalContext.current
    val kinds = listOf(READING to s.shared("variable_term_reading"), CONSTANT to s.shared("variable_term_constant"), VARIABLE to s.shared("variable_term_variable"))
    val kind = kinds.firstOrNull { term.has(it.first) }?.first ?: CONSTANT
    UI.FormSelection(label = s.shared("variable_term_kind"), options = kinds.map { it.second }, selected = kinds.first { it.first == kind }.second, onSelect = { label ->
        onChange(when (kinds.first { it.second == label }.first) {
            READING -> JSONObject().put(READING, JSONObject().put("selection", JSONObject()).put("reduction", Reduction.COUNT.name))
            VARIABLE -> JSONObject().put(VARIABLE, "")
            else -> JSONObject().put(CONSTANT, JSONObject.NULL)
        })
    }, required = true)
    when (kind) {
        CONSTANT -> FieldInput(
            constantField.copy(displayName = s.shared("variable_value")),
            JsonUtils.toValue(term.opt(CONSTANT)?.takeIf { it != JSONObject.NULL }),
            { value -> onChange(if (value == null) JSONObject().put(CONSTANT, JSONObject.NULL) else Term.Constant(value).toJson()) },
            context, required = true
        )
        VARIABLE -> FieldInput(
            FieldDefinition(VARIABLE, s.shared("variable_term_variable"), null, FieldType.REFERENCE, false,
                mapOf(ReferenceTarget.TARGET to mapOf(ReferenceTarget.KINDS to listOf(ReferenceKind.VARIABLE.name)))),
            term.optString(VARIABLE).takeIf { it.isNotEmpty() }?.let { mapOf("kind" to ReferenceKind.VARIABLE.name, "id" to it) },
            { value -> onChange(JSONObject().put(VARIABLE, ReferenceTarget.referenceOf(value)?.id ?: "")) },
            context, required = true
        )
        else -> ReadingPicker(term.getJSONObject(READING), s) { reading -> onChange(JSONObject().put(READING, reading)) }
    }
}

private const val READING = "reading"
private const val CONSTANT = "constant"
private const val VARIABLE = "variable"
