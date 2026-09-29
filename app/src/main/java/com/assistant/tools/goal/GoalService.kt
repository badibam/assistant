package com.assistant.tools.goal

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.Source
import com.assistant.core.coordinator.currentOrigin
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.AppDatabase
import com.assistant.core.selection.EntryPeriod
import com.assistant.core.selection.EntrySelection
import com.assistant.core.selection.Reference
import com.assistant.core.selection.ReferenceKind
import com.assistant.core.selection.TimePoint
import com.assistant.core.services.ExecutableService
import com.assistant.core.services.OperationResult
import com.assistant.core.strings.Strings
import com.assistant.core.utils.DataChangeNotifier
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject

/**
 * The operations of a goal's attempts (docs/design/missing-tools.md, « Objectif »), for the screen
 * and the AI alike:
 * - evaluate: `id` — each criterion's value and whether it is met, each sub-goal's, the verdict so
 *   far; a locked attempt gives the judgement frozen when it was validated
 * - validate: `id` — computes and freezes the verdict and the values read, who validated (the
 *   call's origin: a person or the AI, never a scheduler) and when; refused while a value is missing
 * - reopen: `id` — a validated or expired attempt back to be validated, which stays in its history
 *
 * An attempt still open follows the goal's current definition; a locked one keeps the copy it was
 * judged by.
 */
class GoalService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(tool = "goal", context = context)
    private val coordinator = Coordinator(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        val id = params.optString("id").takeIf { it.isNotEmpty() } ?: return OperationResult.error(s.shared("service_error_missing_id"))
        val attempt = loadAttempt(id) ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(id))
        return try {
            when (operation) {
                "evaluate" -> OperationResult.success(judgementMap(attempt).filterValues { it != null }.mapValues { it.value!! })
                "validate" -> validate(attempt)
                "reopen" -> reopen(attempt)
                else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
            }
        } catch (e: IllegalStateException) {
            // A read that failed: the operation's error, never a criterion's cause
            OperationResult.error(e.message ?: "")
        }
    }

    /** An attempt as the service needs it. */
    private data class Attempt(
        val id: String,
        val toolInstanceId: String,
        val start: Long,
        val data: JSONObject,
        val state: JSONObject,
        val config: JSONObject
    ) {
        val status: String get() = state.optString(GoalToolType.STATUS)
        val periodEnd: Long? get() = (state.opt(GoalToolType.PERIOD_END) as? Number)?.toLong()
        val locked: Boolean get() = status in GoalToolType.Status.LOCKED
    }

    private suspend fun loadAttempt(id: String): Attempt? {
        val entry = coordinator.processUserAction("tool_data.get_single", mapOf("entry_id" to id)).data?.get("entry") as? Map<*, *> ?: return null
        val toolInstanceId = entry["tool_instance_id"] as String
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        val config = ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
            ?: throw IllegalStateException(tool.error ?: s.shared("service_error_tool_instance_not_found"))
        @Suppress("UNCHECKED_CAST")
        fun obj(key: String) = (entry[key] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) } ?: JSONObject()
        return Attempt(id, toolInstanceId, (entry["timestamp"] as Number).toLong(), obj("data"), obj("state"), config)
    }

    /** The definition an attempt is judged by: the goal's while it is open, its own copy once locked. */
    private fun definitionOf(attempt: Attempt): GoalDefinition =
        if (attempt.locked) GoalDefinition.of(JSONObject(attempt.data.optString(GoalToolType.DEFINITION, "{}")))
        else GoalDefinition.of(attempt.config)

    /** The instant a read criterion is read at: the end of the period, now while it runs. */
    private fun readAt(attempt: Attempt): Long = minOf(System.currentTimeMillis(), attempt.periodEnd ?: Long.MAX_VALUE)

    /** Each criterion's value by key, and why a read one has none. */
    private suspend fun values(attempt: Attempt, definition: GoalDefinition): Pair<Map<String, Any?>, Map<String, String>> {
        val values = mutableMapOf<String, Any?>()
        val failures = mutableMapOf<String, String>()
        val at = readAt(attempt)
        for (criterion in definition.allCriteria) {
            if (criterion.kind.entered != null) {
                values[criterion.key] = attempt.data.opt(criterion.key)?.takeIf { it != JSONObject.NULL }
                continue
            }
            val result = when (criterion.kind) {
                CriterionKind.VARIABLE -> coordinator.processUserAction("variables.evaluate", mapOf("variable_id" to criterion.variable, "at" to listOf(at)))
                else -> coordinator.processUserAction("readings.read", mapOf(
                    "selection" to JsonUtils.toMap(EntrySelection(
                        target = Reference(ReferenceKind.TOOL_INSTANCE, criterion.tool ?: ""),
                        period = EntryPeriod(TimePoint.Fixed(attempt.start), TimePoint.Fixed(at))
                    ).toJson()),
                    "field" to (criterion.field ?: ""),
                    "reduction" to (criterion.reduction?.name ?: ""),
                    "reference" to at
                ))
            }
            if (!result.isSuccess) throw IllegalStateException(result.error ?: "")
            val row = ((result.data?.get("values") as? List<*>)?.firstOrNull() as? Map<*, *>) ?: result.data
            val failure = row?.get("failure") as? Map<*, *>
            if (failure != null) failures[criterion.key] = failure["message"] as? String ?: "" else values[criterion.key] = row?.get("value")
        }
        return values to failures
    }

    /** The judgement of [attempt] as the screen and the AI read it. */
    private suspend fun judgementMap(attempt: Attempt): Map<String, Any?> {
        if (attempt.locked && attempt.state.has(GoalToolType.JUDGEMENT)) {
            return JsonUtils.toMap(JSONObject(attempt.state.getString(GoalToolType.JUDGEMENT))) + mapOf("status" to attempt.status, "frozen" to true)
        }
        val definition = definitionOf(attempt)
        val (values, failures) = values(attempt, definition)
        val judged = GoalJudge.judge(definition, values)
        return mapOf(
            "status" to attempt.status,
            "frozen" to false,
            "verdict" to judged.verdict.name,
            "met" to judged.met,
            "required" to judged.required,
            "read_at" to readAt(attempt),
            "criteria" to definition.allCriteria.map { criterion ->
                mapOf(
                    "key" to criterion.key,
                    "name" to criterion.name,
                    "value" to values[criterion.key],
                    "met" to judged.criteria.getValue(criterion.key).second.name,
                    "failure" to failures[criterion.key]
                )
            },
            "sub_goals" to judged.subGoals.mapValues { it.value.name }
        )
    }

    private suspend fun validate(attempt: Attempt): OperationResult {
        val origin = currentOrigin()
        if (origin != Source.USER && origin != Source.AI) return OperationResult.error(s.tool("error_validate_origin"))
        if (attempt.locked) return OperationResult.error(s.tool("error_locked"))
        val judgement = judgementMap(attempt)
        if (judgement["verdict"] == Met.UNKNOWN.name) {
            val missing = (judgement["criteria"] as List<*>).filterIsInstance<Map<*, *>>().filter { it["met"] == Met.UNKNOWN.name }
                .joinToString(", ") { c -> listOfNotNull(c["name"] as? String, c["failure"] as? String).joinToString(" : ") }
            return OperationResult.error(s.tool("error_verdict_unknown").format(missing))
        }
        val status = if (judgement["verdict"] == Met.YES.name) GoalToolType.Status.SUCCEEDED else GoalToolType.Status.FAILED
        val now = System.currentTimeMillis()
        val result = coordinator.processUserAction("tool_data.update", mapOf(
            "id" to attempt.id,
            // The definition it was judged by, frozen with it
            "data" to mapOf(GoalToolType.DEFINITION to GoalDefinition.copyOf(attempt.config).toString()),
            "state" to mapOf(
                GoalToolType.STATUS to status,
                GoalToolType.JUDGEMENT to JsonUtils.toJSONObject(judgement.filterKeys { it != "frozen" && it != "status" }).toString(),
                GoalToolType.VALIDATED_BY to origin.name.lowercase(),
                GoalToolType.VALIDATED_AT to now
            )
        ))
        return if (result.isSuccess) OperationResult.success(mapOf("id" to attempt.id, "status" to status)) else OperationResult.error(result.error ?: "")
    }

    /**
     * Back to be validated, the values read unfrozen: the goal's own write, the only one a locked
     * attempt takes, which keeps when it was reopened.
     */
    private suspend fun reopen(attempt: Attempt): OperationResult {
        val origin = currentOrigin()
        if (origin != Source.USER && origin != Source.AI) return OperationResult.error(s.tool("error_validate_origin"))
        if (!attempt.locked) return OperationResult.error(s.tool("error_not_locked"))
        val dao = AppDatabase.getDatabase(context).toolDataDao()
        val entity = dao.getById(attempt.id) ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(attempt.id))
        val state = JSONObject(attempt.state.toString()).apply {
            put(GoalToolType.STATUS, GoalToolType.Status.TO_VALIDATE)
            remove(GoalToolType.JUDGEMENT); remove(GoalToolType.VALIDATED_BY); remove(GoalToolType.VALIDATED_AT)
            put(GoalToolType.REOPENED_AT, System.currentTimeMillis())
        }
        dao.update(entity.copy(state = state.toString(), updatedAt = System.currentTimeMillis()))
        AppDatabase.getDatabase(context).toolInstanceDao().getToolInstanceById(attempt.toolInstanceId)?.let {
            DataChangeNotifier.notifyToolDataChanged(attempt.toolInstanceId, it.zone_id)
        }
        return OperationResult.success(mapOf("id" to attempt.id, "status" to GoalToolType.Status.TO_VALIDATE))
    }

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(tool = "goal", context = context)
        return when (operation) {
            "validate" -> s.tool("verbalize_validate")
            "reopen" -> s.tool("verbalize_reopen")
            else -> s.tool("verbalize_evaluate")
        }
    }
}
