package com.assistant.core.terms

import android.content.Context
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.FieldType
import com.assistant.core.fields.toFieldDefinition
import com.assistant.core.selection.EntryPeriod
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * A term read once, at the context's reference: a constant as it is written, a variable through
 * `variables.evaluate`, a reading through `readings.read`. What a condition judged once compares.
 */
class TermReader(private val context: Context) {

    /** What reading a term gives. */
    sealed interface Read {
        /** A value, and the field it is a value of; none for a constant, which has no type of its own. */
        data class Value(val value: Any?, val field: FieldDefinition?) : Read
        /** No value, and why in words: a reading without entries, a variable that failed. */
        data class Failed(val message: String) : Read
    }

    /**
     * [term] read at [at].
     *
     * @param period The period a reading without one of its own reads (a goal's attempt); null
     *   for its whole history
     * @throws IllegalStateException when a read cannot run: an unknown tool or variable, a field
     *   the tool has no more, a formula per entry (read in a variable only)
     */
    suspend fun read(term: Term, at: Long, period: EntryPeriod?): Read = when (term) {
        is Term.Constant -> Read.Value(term.value, null)
        is Term.Variable -> {
            val result = Coordinator(context).processUserAction("variables.evaluate", mapOf("variable_id" to term.id, "at" to listOf(at)))
            if (!result.isSuccess) throw IllegalStateException(result.error ?: "")
            val row = (result.data?.get("values") as? List<*>)?.firstOrNull() as? Map<*, *>
            val field = (result.data?.get("field") as? Map<*, *>)?.let { fieldOf(it, result.data?.get("name") as? String ?: term.id) }
            (row?.get("failure") as? Map<*, *>)?.let { Read.Failed(it["message"] as? String ?: "") } ?: Read.Value(row?.get("value"), field)
        }
        is Term.Reading -> {
            if (term.perEntry != null) throw IllegalStateException("a formula per entry is read in a variable")
            val selection = if (term.selection.period.isEmpty && period != null) term.selection.copy(period = period) else term.selection
            val result = Coordinator(context).processUserAction("readings.read", buildMap {
                put("selection", JsonUtils.toMap(selection.toJson()))
                term.field?.let { put("field", it) }
                put("reduction", term.reduction.name)
                put("reference", at)
            })
            if (!result.isSuccess) throw IllegalStateException(result.error ?: "")
            (result.data?.get("failure") as? Map<*, *>)?.let { Read.Failed(it["message"] as? String ?: "") }
                // A count has no field: it is a number
                ?: Read.Value(result.data?.get("value"), (result.data?.get("field") as? Map<*, *>)?.let { fieldOf(it, term.field ?: "") }
                    ?: FieldDefinition("count", "count", null, FieldType.NUMERIC, false, mapOf("decimals" to 0)))
        }
    }

    private fun fieldOf(json: Map<*, *>, name: String): FieldDefinition {
        val obj = JsonUtils.toJSONObject(json.entries.associate { it.key.toString() to it.value })
        if (!obj.has("name")) obj.put("name", name)
        if (!obj.has("display_name")) obj.put("display_name", name)
        return obj.toFieldDefinition()
    }
}
