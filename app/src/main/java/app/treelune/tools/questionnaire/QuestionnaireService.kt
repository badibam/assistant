package app.treelune.tools.questionnaire

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import org.json.JSONObject

/**
 * A questionnaire's operations, for the screen and the AI alike:
 * - complete: `id` — the entry is filled, when it was filled kept; its answers are written as any
 *   field before (tool_data.update), gaps allowed
 * - ignore: `id` — a gap chosen, which stays in the history
 * - ignore_all: every entry still to fill, back from an absence
 */
class QuestionnaireService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(tool = "questionnaire", context = context)
    private val coordinator = Coordinator(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        return when (operation) {
            "complete" -> setStatus(params.optString("id"), QuestionnaireToolType.Status.FILLED)
            "ignore" -> setStatus(params.optString("id"), QuestionnaireToolType.Status.IGNORED)
            "ignore_all" -> ignoreAll(params.optString("tool_instance_id"))
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun setStatus(id: String, status: String): OperationResult {
        if (id.isEmpty()) return OperationResult.error(s.shared("service_error_missing_id"))
        val state = mutableMapOf<String, Any>(QuestionnaireToolType.STATUS to status)
        if (status == QuestionnaireToolType.Status.FILLED) state[QuestionnaireToolType.FILLED_AT] = System.currentTimeMillis()
        val result = coordinator.processUserAction("tool_data.update", mapOf("id" to id, "state" to state))
        return if (result.isSuccess) OperationResult.success(mapOf("id" to id, "status" to status)) else OperationResult.error(result.error ?: "")
    }

    private suspend fun ignoreAll(toolInstanceId: String): OperationResult {
        if (toolInstanceId.isEmpty()) return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        val result = coordinator.processUserAction("tool_data.get", mapOf(
            "tool_instance_id" to toolInstanceId,
            "fields" to listOf("id"),
            "filters" to listOf(app.treelune.core.conditions.Conditions.onField("state.${QuestionnaireToolType.STATUS}", "in", listOf(QuestionnaireToolType.Status.TO_FILL)))
        ))
        if (!result.isSuccess) return OperationResult.error(result.error ?: "")
        val ids = (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { it["id"] as String }
        for (id in ids) {
            val done = setStatus(id, QuestionnaireToolType.Status.IGNORED)
            if (!done.success) return done
        }
        return OperationResult.success(mapOf("ignored" to ids.size))
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(tool = "questionnaire", context = context)
        return s.tool("verbalize_$operation").takeIf { operation in setOf("complete", "ignore", "ignore_all") } ?: s.shared("action_verbalize_unknown")
    }
}
