package com.assistant.tools.goal

import android.content.Context
import com.assistant.core.coordinator.CancellationToken
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.Source
import com.assistant.core.coordinator.currentOrigin
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.database.AppDatabase
import com.assistant.core.conditions.Condition
import com.assistant.core.conditions.ConditionJudge
import com.assistant.core.conditions.Conditions
import com.assistant.core.fields.FieldDefinition
import com.assistant.core.fields.toJson
import com.assistant.core.selection.EntryPeriod
import com.assistant.core.selection.TimePoint
import com.assistant.core.selection.TimeResolver
import com.assistant.core.terms.TermReader
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

    /**
     * The definition an attempt is judged by: the goal's while it is open, its own copy once locked.
     *
     * @throws IllegalStateException for a locked attempt without its copy, which an attempt opened
     *         before the copy was kept may be: judged by no definition, it would say nothing true
     */
    private fun definitionOf(attempt: Attempt): GoalDefinition =
        if (attempt.locked) GoalDefinition.of(JSONObject(
            attempt.data.optString(GoalToolType.DEFINITION).takeIf { it.isNotEmpty() } ?: throw IllegalStateException(s.tool("error_no_definition"))
        ))
        else GoalDefinition.of(attempt.config)

    /** The instant a read criterion is read at: the end of the period, now while it runs. */
    private fun readAt(attempt: Attempt): Long = minOf(System.currentTimeMillis(), attempt.periodEnd ?: Long.MAX_VALUE)

    /**
     * A criterion judged: its value (the left side), what it is compared with (the right side,
     * read), the field both are values of, whether it is met; or why it could not be read.
     */
    private data class Judged(val value: Any?, val right: List<Any?>, val field: FieldDefinition?, val met: Met, val failure: String?)

    /**
     * Each criterion judged, by key. Its terms are read at the end of the attempt, now while it
     * runs; a reading without a period of its own reads the attempt's.
     */
    private suspend fun judged(attempt: Attempt, definition: GoalDefinition): Map<String, Judged> {
        val at = readAt(attempt)
        val period = EntryPeriod(TimePoint.Fixed(attempt.start), TimePoint.Fixed(at))
        val reader = TermReader(context)
        val text = { key: String -> s.shared(key) }
        return definition.allCriteria.associate { criterion ->
            criterion.key to try {
                val condition = Condition.fromJson(criterion.condition, criterion.name, text)
                val entered = criterion.enteredField()
                // One side read: its value and its field, or why it has none
                suspend fun side(side: Condition.Side): TermReader.Read = when (side) {
                    is Condition.Side.Of -> reader.read(side.term, at, period)
                    is Condition.Side.Field -> if (entered != null) TermReader.Read.Value(attempt.data.opt(criterion.key)?.takeIf { it != JSONObject.NULL }?.let { JsonUtils.toValue(it) }, entered)
                        else throw IllegalArgumentException(s.tool("error_criterion_field").format(criterion.name))
                }
                val sides = listOf(side(if (entered != null) Condition.Side.Field(Criterion.enteredPath(criterion.key)) else condition.left)) + condition.right.map { side(it) }
                val failure = sides.filterIsInstance<TermReader.Read.Failed>().firstOrNull()
                if (failure != null) Judged(null, emptyList(), null, Met.UNKNOWN, failure.message)
                else {
                    val values = sides.map { it as TermReader.Read.Value }
                    // Compared as the side that has a type: a constant takes the other's
                    val field = values.firstNotNullOfOrNull { it.field }
                        ?: throw IllegalArgumentException(s.tool("error_criterion_constants").format(criterion.name))
                    val holds = ConditionJudge.holds(field, condition.op, values[0].value, values.drop(1).map { it.value }, TimeResolver.at(at), text)
                    Judged(values[0].value, values.drop(1).map { it.value }, field, when (holds) { true -> Met.YES; false -> Met.NO; null -> Met.UNKNOWN }, null)
                }
            } catch (e: IllegalArgumentException) {
                // A condition that does not read is said on its criterion, never judged
                Judged(null, emptyList(), null, Met.UNKNOWN, e.message)
            }
        }
    }

    /** The judgement of [attempt] as the screen and the AI read it. */
    private suspend fun judgementMap(attempt: Attempt): Map<String, Any?> {
        if (attempt.locked && attempt.state.has(GoalToolType.JUDGEMENT)) {
            return JsonUtils.toMap(JSONObject(attempt.state.getString(GoalToolType.JUDGEMENT))) + mapOf("status" to attempt.status, "frozen" to true)
        }
        val definition = definitionOf(attempt)
        val judged = judged(attempt, definition)
        val judgement = GoalJudge.judge(definition, judged.mapValues { it.value.value to it.value.met })
        return mapOf(
            "status" to attempt.status,
            "frozen" to false,
            "verdict" to judgement.verdict.name,
            "met" to judgement.met,
            "required" to judgement.required,
            "read_at" to readAt(attempt),
            "criteria" to definition.allCriteria.map { criterion ->
                val one = judged.getValue(criterion.key)
                mapOf(
                    "key" to criterion.key,
                    "name" to criterion.name,
                    "value" to one.value,
                    "op" to criterion.condition.optString(Conditions.OP),
                    "compared" to one.right,
                    "field" to one.field?.let { JsonUtils.toMap(it.toJson()) },
                    "met" to one.met.name,
                    "failure" to one.failure
                )
            },
            "sub_goals" to judgement.subGoals.mapValues { it.value.name }
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
