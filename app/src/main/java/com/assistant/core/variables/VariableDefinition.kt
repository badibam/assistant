package com.assistant.core.variables

import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.terms.Term
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * What a variable is, as `definition_json` stores it (docs/DATA.md, « Variables »): a constant
 * (`objectif_calorique = 2100`), or a formula on named terms. Its [field] is the type and
 * settings of its value, NUMERIC, SCALE or DURATION: a formula reads numbers.
 */
sealed interface VariableDefinition {
    val field: FieldDefinition

    /** [value] in the stored form of [field]: a number, milliseconds for a DURATION. */
    data class Constant(val value: Double, override val field: FieldDefinition) : VariableDefinition

    /**
     * [formula] as stored, a variable it names written `{var:ID}`; [terms], its local names.
     */
    data class Computed(val formula: String, val terms: Map<String, Term>, override val field: FieldDefinition) : VariableDefinition

    fun toJson(): JSONObject = when (this) {
        is Constant -> JSONObject().put("kind", CONSTANT).put("value", value).put("field", fieldJson(field))
        is Computed -> JSONObject().put("kind", FORMULA).put("formula", formula)
            .put("terms", JSONObject().apply { terms.forEach { (name, term) -> put(name, term.toJson()) } })
            .put("field", fieldJson(field))
    }

    companion object {
        const val CONSTANT = "CONSTANT"
        const val FORMULA = "FORMULA"

        /** The types a variable's value takes. */
        val VALUE_TYPES = setOf(FieldType.NUMERIC, FieldType.SCALE, FieldType.DURATION)

        /**
         * @param name The variable's name, which its field carries
         * @throws IllegalArgumentException naming what does not read
         */
        fun fromJson(json: JSONObject, name: String, text: (String) -> String): VariableDefinition {
            val field = json.optJSONObject("field")?.let { fieldOf(it, name, text) }
            return when (json.optString("kind")) {
                CONSTANT -> Constant(
                    value = (json.opt("value") as? Number)?.toDouble() ?: throw IllegalArgumentException(text("variable_error_constant_value")),
                    field = field ?: throw IllegalArgumentException(text("variable_error_field").format(name))
                )
                FORMULA -> Computed(
                    formula = json.optString("formula").takeIf { it.isNotBlank() } ?: throw IllegalArgumentException(text("variable_error_formula_missing")),
                    terms = json.optJSONObject("terms")?.let { terms ->
                        terms.keys().asSequence().associateWith { Term.fromJson(terms.getJSONObject(it), it, text) }
                    } ?: emptyMap(),
                    field = field ?: bareField(name)
                )
                else -> throw IllegalArgumentException(text("variable_error_kind").format(json.optString("kind")))
            }
        }

        /** Stands for a formula's field until the service deduces it: a bare number. */
        fun bareField(name: String) = FieldDefinition(name, name, null, FormulaType.BARE.type, false, FormulaType.BARE.config)

        fun fieldJson(field: FieldDefinition): JSONObject = JSONObject().put("type", field.type.name).apply {
            field.config?.let { put("config", JsonUtils.toJSONObject(it)) }
        }

        private fun fieldOf(json: JSONObject, name: String, text: (String) -> String): FieldDefinition {
            val type = FieldType.entries.firstOrNull { it.name == json.optString("type") }?.takeIf { it in VALUE_TYPES }
                ?: throw IllegalArgumentException(text("variable_error_type").format(json.optString("type"), VALUE_TYPES.joinToString(", ") { it.name }))
            return FieldDefinition(name, name, null, type, false, json.optJSONObject("config")?.let { JsonUtils.toMap(it) }?.mapValues { it.value!! })
        }
    }
}
