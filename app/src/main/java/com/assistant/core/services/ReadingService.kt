package com.assistant.core.services

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.fields.ToolFields
import com.assistant.core.fields.toJson
import com.assistant.core.reading.FailureReason
import com.assistant.core.reading.FieldReading
import com.assistant.core.reading.ReadingResult
import com.assistant.core.reading.Reduction
import com.assistant.core.selection.EntrySelection
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.selection.TimeResolver
import com.assistant.core.strings.Strings
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * The core's reading: one value from the entries of one tool instance (docs/DATA.md, « Lecture
 * du cœur »), the door every reader goes through -- a goal's criterion, a variable's term.
 *
 * - read, a variable: `variable` (its name) and `at`, the instants to read it at (milliseconds);
 *   a value or a failure per instant, in the same order (the variables service computes it).
 * - read, a field: `selection` (a tool instance, its period and filters, relative dates resolved against
 *   `reference`, milliseconds, which the reader gives: a goal's attempt end, the instant a
 *   variable is read at), `field` (a path, absent to count) and `reduction`. It gives `value` with
 *   `field`, the type and settings of the result, or `failure` with its causes as data: a reading
 *   without a value is an answer, which each reader decides what to do with.
 */
class ReadingService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(context = context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "read" -> read(params)
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun read(params: JSONObject): OperationResult {
        // A variable: computed at each instant asked, by the variables service
        params.optString("variable").takeIf { it.isNotEmpty() }?.let { name ->
            val result = Coordinator(context).processUserAction("variables.evaluate", mapOf(
                "name" to name,
                "at" to (params.optJSONArray("at")?.let { JsonUtils.toList(it) }
                    ?: return OperationResult.error(s.shared("service_error_reading_param").format("at")))
            ))
            return if (result.isSuccess) OperationResult.success(result.data ?: emptyMap()) else OperationResult.error(result.error ?: "")
        }
        val selection = try {
            EntrySelection.fromJson(params.optJSONObject("selection")
                ?: return OperationResult.error(s.shared("service_error_reading_param").format("selection"))) { s.shared(it) }
        } catch (e: IllegalArgumentException) {
            return OperationResult.error(e.message ?: s.shared("service_error_reading_param").format("selection"))
        }
        if (selection.target.kind != ReferenceKind.TOOL_INSTANCE) {
            return OperationResult.error(s.shared("service_error_reading_source").format(selection.target.kind.name))
        }
        val reference = (params.opt("reference") as? Number)?.toLong()
            ?: return OperationResult.error(s.shared("service_error_reading_param").format("reference"))
        val reduction = Reduction.entries.firstOrNull { it.name == params.optString("reduction") }
            ?: return OperationResult.error(s.shared("service_error_reading_reduction").format(
                params.optString("reduction"), Reduction.entries.joinToString(", ") { it.name }))
        val toolInstanceId = selection.target.id!!

        // A source deleted since is an answer, not an error: whoever reads decides what it means.
        // A read that fails is an error, never taken for a deletion
        when (sourceGone(toolInstanceId)) {
            null -> return OperationResult.error(s.shared("service_error_reading_source_unread"))
            true -> return OperationResult.success(failure(ReadingResult.Failure(FailureReason.SOURCE_NOT_FOUND, null)))
            false -> {}
        }

        val fields = ToolFields.filterable(toolInstanceId, context, s)
        val path = params.optString("field").takeIf { it.isNotEmpty() }
        val field = path?.let { fields[it] ?: return OperationResult.error(
            s.shared("service_error_filter_unknown_field").format(it, fields.keys.joinToString(", "))) }
        if (reduction !in Reduction.forType(field?.type)) {
            return OperationResult.error(s.shared("service_error_reading_reduction_type").format(
                reduction.name, field?.type?.name ?: "-", Reduction.forType(field?.type).joinToString(", ") { it.name }))
        }

        val filters = try {
            selection.storedFilters(fields, TimeResolver.at(reference)) { s.shared(it) }
        } catch (e: IllegalArgumentException) {
            return OperationResult.error(e.message ?: "")
        }
        val entries = Coordinator(context).processUserAction("tool_data.get", mapOf(
            "tool_instance_id" to toolInstanceId,
            "filters" to JsonUtils.toList(filters)
        ))
        if (!entries.isSuccess) return OperationResult.error(entries.error ?: "")
        val rows = (entries.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<String, Any?>>()

        return OperationResult.success(when (val result = FieldReading.reduce(rows, path, field, reduction)) {
            is ReadingResult.Value -> mapOf("value" to result.value, "field" to JsonUtils.toMap(result.field.toJson()))
            is ReadingResult.Failure -> failure(result)
        })
    }

    /** Whether the tool instance was deleted; null when that cannot be read. */
    private suspend fun sourceGone(toolInstanceId: String): Boolean? {
        val result = Coordinator(context).processUserAction("references.names", mapOf("references" to listOf(mapOf("kind" to "TOOL_INSTANCE", "id" to toolInstanceId))))
        if (!result.isSuccess) return null
        return ((result.data?.get("references") as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("deleted") as? Boolean
    }

    /** A failure as data, with its words for whoever shows it. */
    private fun failure(failure: ReadingResult.Failure): Map<String, Any> = mapOf("failure" to mapOf(
        "reason" to failure.reason.name,
        "field" to failure.field,
        "entries" to failure.entries,
        "message" to when (failure.reason) {
            FailureReason.NO_ENTRY -> s.shared("reading_failure_no_entry").format(failure.field ?: "")
            FailureReason.MISSING_VALUE -> s.shared("reading_failure_missing_value").format(failure.entries.size, failure.field ?: "")
            FailureReason.SOURCE_NOT_FOUND -> s.shared("reading_failure_source_not_found")
        }
    ))

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(context = context)
        return when (operation) {
            "read" -> s.shared("action_verbalize_readings_read")
            else -> s.shared("action_verbalize_unknown")
        }
    }
}
