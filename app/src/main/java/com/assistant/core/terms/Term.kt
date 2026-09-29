package com.assistant.core.terms

import com.assistant.core.reading.Reduction
import com.assistant.core.selection.EntrySelection
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * The Terme brick (docs/BRICKS.md): a value a formula or a condition reads, written once, taken
 * from a variable, or read from entries. Stored as `{"constant": …}`, `{"variable": "<id>"}` or
 * `{"reading": {…}}`.
 */
sealed interface Term {

    /** The kind of a term, which a setting offers among (SettingNode.Term): its stored form's key. */
    enum class Kind(val key: String) { CONSTANT("constant"), VARIABLE("variable"), READING("reading") }


    /**
     * A reading of the core without a test: a field reduced across the selected entries, or,
     * with [perEntry], a formula computed in each entry then reduced ("quantité × aliment.kcal_100g
     * / 100", summed over the meals). The selection's period is relative to the instant the
     * term is read at.
     */
    data class Reading(val selection: EntrySelection, val field: String?, val reduction: Reduction, val perEntry: String? = null) : Term

    /**
     * A value written once, in the stored form of what it is read as, in its Kotlin form (a list
     * for several options). It has no type of its own: a formula reads it as a number, a condition
     * as the field on its other side.
     */
    data class Constant(val value: Any) : Term

    /** A variable, by its id. */
    data class Variable(val id: String) : Term

    fun toJson(): JSONObject = when (this) {
        is Reading -> JSONObject().put("reading", JSONObject().apply {
            put("selection", selection.toJson())
            field?.let { put("field", it) }
            perEntry?.let { put("per_entry", it) }
            put("reduction", reduction.name)
        })
        is Constant -> JSONObject().put("constant", when (value) {
            is List<*> -> JsonUtils.toJSONArray(value)
            is Map<*, *> -> JsonUtils.toJSONObject(value.entries.associate { it.key.toString() to it.value })
            else -> value
        })
        is Variable -> JSONObject().put("variable", id)
    }

    companion object {
        /**
         * @param name What the term is called where it is used, for the error
         * @throws IllegalArgumentException naming what does not read
         */
        fun fromJson(json: JSONObject, name: String, text: (String) -> String): Term = when {
            json.has("reading") -> json.getJSONObject("reading").let { reading ->
                Reading(
                    selection = EntrySelection.fromJson(reading.optJSONObject("selection")
                        ?: throw IllegalArgumentException(text("variable_error_term").format(name)), text),
                    field = reading.optString("field").takeIf { it.isNotEmpty() },
                    reduction = Reduction.entries.firstOrNull { it.name == reading.optString("reduction") }
                        ?: throw IllegalArgumentException(text("service_error_reading_reduction").format(reading.optString("reduction"), Reduction.entries.joinToString(", ") { it.name })),
                    perEntry = reading.optString("per_entry").takeIf { it.isNotBlank() }
                )
            }
            json.has("constant") && !json.isNull("constant") -> Constant(JsonUtils.toValue(json.get("constant"))!!)
            json.optString("variable").isNotEmpty() -> Variable(json.getString("variable"))
            else -> throw IllegalArgumentException(text("variable_error_term").format(name))
        }
    }
}
