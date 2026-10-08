package app.treelune.tools.sequence

import android.content.Context
import app.treelune.core.coordinator.CancellationToken
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.Source
import app.treelune.core.coordinator.currentOrigin
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.database.AppDatabase
import app.treelune.core.database.entities.ToolDataEntity
import app.treelune.core.services.ExecutableService
import app.treelune.core.services.OperationResult
import app.treelune.core.strings.Strings
import app.treelune.core.utils.JsonUtils
import org.json.JSONObject

/**
 * A session's operations (docs/design/sequence-tool.md, « Les opérations »).
 *
 * For the screen, the notification and the AI:
 * - log_after: `timestamp`, `duration`, `id` — a session done, all its steps counted, noted after
 *   the fact; `id` a planned entry it fills, a new entry without
 * - ignore: `id` — a planned session left undone, which stays in the history
 * - ignore_all: every planned session of the tool
 *
 * For the screen and the notification only, refused to the AI and to an outside client: they
 * have a meaning only for whoever does the session.
 * - start: the session begins, on the planned entry waiting if there is one, a new one if not
 * - done, skip, back, restart, pause, resume, extend, stop: `id` — the gestures (SequenceGestures)
 * - resume_interrupted: `id` — a session the app stopped mid-way goes on
 * - advance: `id` — the timers run out by now passed, which the running session asks itself
 *
 * Every gesture reads the run from the entry, applies itself and writes the run back: an
 * interrupted session is never lost. The session running hears of each one (SequenceLive).
 */
class SequenceService(private val context: Context) : ExecutableService {

    private val s = Strings.`for`(tool = "sequence", context = context)
    private val coordinator = Coordinator(context)

    override suspend fun execute(operation: String, params: JSONObject, token: CancellationToken): OperationResult {
        if (token.isCancelled) return OperationResult.cancelled()
        if (operation in GESTURES || operation == START) {
            val origin = currentOrigin()
            if (origin != Source.USER && origin != Source.SYSTEM) return OperationResult.error(s.tool("error_gesture_origin"))
        }
        return when (operation) {
            START -> start(params.optString("tool_instance_id"))
            LOG_AFTER -> logAfter(params)
            IGNORE -> ignore(params.optString("id"))
            IGNORE_ALL -> ignoreAll(params.optString("tool_instance_id"))
            in GESTURES -> gesture(operation, params.optString("id"))
            else -> OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
    }

    private suspend fun start(toolInstanceId: String): OperationResult {
        if (toolInstanceId.isEmpty()) return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        runningElsewhere()?.let { (tool, name) ->
            return OperationResult.error(if (tool == toolInstanceId) s.tool("error_already_running") else s.tool("error_running_elsewhere").format(name))
        }
        val config = configOf(toolInstanceId)
        val steps = SequencePlan.unroll(config)
        if (steps.isEmpty()) return OperationResult.error(s.tool("error_no_steps"))
        val now = System.currentTimeMillis()
        val run = SequenceRun.start(steps, now)
        val state = mapOf(
            SequenceToolType.STATUS to SequenceToolType.Status.RUNNING,
            SequenceToolType.STARTED_AT to now,
            SequenceToolType.RUN to run.toJson().toString()
        )
        // The planned session waiting, the latest, is the one done; without one, a new entry
        val planned = entriesOf(toolInstanceId, SequenceToolType.Status.PLANNED).maxByOrNull { it.timestamp ?: 0L }
        val result = if (planned != null) {
            coordinator.processUserAction("tool_data.update", mapOf("id" to planned.id, "state" to state))
        } else {
            coordinator.processUserAction("tool_data.create", mapOf(
                "tool_instance_id" to toolInstanceId,
                "timestamp" to now,
                "data" to emptyMap<String, Any>(),
                "state" to state
            ))
        }
        if (!result.isSuccess) return OperationResult.error(result.error ?: "")
        val id = planned?.id ?: (result.data?.get("id") as String)
        SequenceLive.started(context, id)
        return OperationResult.success(mapOf("id" to id, "status" to SequenceToolType.Status.RUNNING))
    }

    private suspend fun gesture(operation: String, id: String): OperationResult {
        if (id.isEmpty()) return OperationResult.error(s.shared("service_error_missing_id"))
        val entry = entryOf(id) ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(id))
        val run = runOf(entry) ?: return OperationResult.error(s.tool("error_not_running"))
        val now = System.currentTimeMillis()
        val step: Step = when (operation) {
            DONE -> SequenceGestures.done(run, now)
            SKIP -> SequenceGestures.skip(run, now)
            STOP -> SequenceGestures.stop(run, now)
            ADVANCE -> SequenceGestures.catchUp(run, now)
            BACK -> Step.Running(SequenceGestures.back(run) ?: return OperationResult.error(s.tool("error_back_first")))
            RESTART -> Step.Running(SequenceGestures.restart(run, now))
            PAUSE -> Step.Running(SequenceGestures.pause(run, now))
            RESUME -> Step.Running(SequenceGestures.resume(run, now))
            EXTEND -> Step.Running(SequenceGestures.extend(run, now) ?: return OperationResult.error(s.tool("error_extend_manual")))
            RESUME_INTERRUPTED -> Step.Running(SequenceGestures.resumeInterrupted(run, entry.updatedAt, now))
            else -> return OperationResult.error(s.shared("service_error_unknown_operation").format(operation))
        }
        // The clock moved nothing: no write, nothing to tell
        if (step is Step.Running && step.run == run) return OperationResult.success(mapOf("id" to id, "status" to SequenceToolType.Status.RUNNING))
        val written = write(id, step)
        if (!written.success) return written
        SequenceLive.changed(context, id, operation, step)
        return written
    }

    /** The run written back; a session over keeps what is left of it, and loses its run. */
    private suspend fun write(id: String, step: Step): OperationResult {
        val params = when (step) {
            is Step.Running -> mapOf("id" to id, "state" to mapOf(SequenceToolType.RUN to step.run.toJson().toString()))
            is Step.Finished -> {
                val status = if (step.completed) SequenceToolType.Status.DONE else SequenceToolType.Status.STOPPED
                mapOf(
                    "id" to id,
                    "data" to mapOf(
                        SequenceToolType.DURATION to step.duration,
                        SequenceToolType.PAUSED to step.pauses,
                        SequenceToolType.STEPS_DONE to step.run.count(Outcome.DONE),
                        SequenceToolType.STEPS_SKIPPED to step.run.count(Outcome.SKIPPED),
                        SequenceToolType.STEPS_NOT_DONE to step.notDone
                    ),
                    "state" to mapOf(
                        SequenceToolType.STATUS to status,
                        SequenceToolType.ENDED_AT to step.endedAt,
                        SequenceToolType.RUN to JSONObject.NULL
                    )
                )
            }
        }
        val result = coordinator.processUserAction("tool_data.update", params)
        if (!result.isSuccess) return OperationResult.error(result.error ?: "")
        val status = if (step is Step.Finished) (if (step.completed) SequenceToolType.Status.DONE else SequenceToolType.Status.STOPPED) else SequenceToolType.Status.RUNNING
        return OperationResult.success(mapOf("id" to id, "status" to status))
    }

    private suspend fun logAfter(params: JSONObject): OperationResult {
        val toolInstanceId = params.optString("tool_instance_id")
        if (toolInstanceId.isEmpty()) return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        val timestamp = (params.opt("timestamp") as? Number)?.toLong()
            ?: return OperationResult.error(s.shared("service_error_missing_required_params").format("timestamp"))
        val steps = SequencePlan.unroll(configOf(toolInstanceId))
        if (steps.isEmpty()) return OperationResult.error(s.tool("error_no_steps"))
        val data = mutableMapOf<String, Any>(
            SequenceToolType.STEPS_DONE to steps.size,
            SequenceToolType.STEPS_SKIPPED to 0,
            SequenceToolType.STEPS_NOT_DONE to 0
        )
        (params.opt(SequenceToolType.DURATION) as? Number)?.let { data[SequenceToolType.DURATION] = it.toLong() }
        val state = mapOf(SequenceToolType.STATUS to SequenceToolType.Status.DONE)
        val plannedId = params.optString("id").takeIf { it.isNotEmpty() }
        val result = if (plannedId != null) {
            val planned = entryOf(plannedId) ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(plannedId))
            if (planned.toolInstanceId != toolInstanceId || statusOf(planned) != SequenceToolType.Status.PLANNED) return OperationResult.error(s.tool("error_not_planned"))
            coordinator.processUserAction("tool_data.update", mapOf("id" to plannedId, "timestamp" to timestamp, "data" to data, "state" to state))
        } else {
            coordinator.processUserAction("tool_data.create", mapOf("tool_instance_id" to toolInstanceId, "timestamp" to timestamp, "data" to data, "state" to state))
        }
        if (!result.isSuccess) return OperationResult.error(result.error ?: "")
        return OperationResult.success(mapOf("id" to (plannedId ?: result.data?.get("id") as String), "status" to SequenceToolType.Status.DONE))
    }

    private suspend fun ignore(id: String): OperationResult {
        if (id.isEmpty()) return OperationResult.error(s.shared("service_error_missing_id"))
        val entry = entryOf(id) ?: return OperationResult.error(s.shared("service_error_entry_not_found").format(id))
        if (statusOf(entry) != SequenceToolType.Status.PLANNED) return OperationResult.error(s.tool("error_not_planned"))
        val result = coordinator.processUserAction("tool_data.update", mapOf("id" to id, "state" to mapOf(SequenceToolType.STATUS to SequenceToolType.Status.IGNORED)))
        return if (result.isSuccess) OperationResult.success(mapOf("id" to id, "status" to SequenceToolType.Status.IGNORED)) else OperationResult.error(result.error ?: "")
    }

    private suspend fun ignoreAll(toolInstanceId: String): OperationResult {
        if (toolInstanceId.isEmpty()) return OperationResult.error(s.shared("service_error_missing_tool_instance_id"))
        val planned = entriesOf(toolInstanceId, SequenceToolType.Status.PLANNED)
        for (entry in planned) {
            val done = ignore(entry.id)
            if (!done.success) return done
        }
        return OperationResult.success(mapOf("ignored" to planned.size))
    }

    /** The session running in the app, its tool and the tool's name; null when none runs. */
    private suspend fun runningElsewhere(): Pair<String, String>? {
        val tools = coordinator.processUserAction("tools.list_all", emptyMap())
        if (!tools.isSuccess) throw IllegalStateException(tools.error ?: "tools.list_all")
        val sessions = (tools.data?.get("tool_instances") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
            .filter { it["tooltype"] == "sequence" }
        for (tool in sessions) {
            val id = tool["id"] as String
            if (entriesOf(id, SequenceToolType.Status.RUNNING).isNotEmpty()) return id to (tool["name"] as? String ?: "")
        }
        return null
    }

    private suspend fun configOf(toolInstanceId: String): JSONObject {
        val tool = coordinator.processUserAction("tools.get", mapOf("tool_instance_id" to toolInstanceId))
        @Suppress("UNCHECKED_CAST")
        return ((tool.data?.get("tool_instance") as? Map<*, *>)?.get("config") as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
            ?: throw IllegalStateException(tool.error ?: s.shared("service_error_tool_instance_not_found"))
    }

    private suspend fun entriesOf(toolInstanceId: String, status: String): List<ToolDataEntity> {
        val result = coordinator.processUserAction("tool_data.get", mapOf(
            "tool_instance_id" to toolInstanceId,
            "fields" to listOf("id"),
            "filters" to listOf(app.treelune.core.conditions.Conditions.onField("state.${SequenceToolType.STATUS}", "in", listOf(status)))
        ))
        if (!result.isSuccess) throw IllegalStateException(result.error ?: "tool_data.get")
        return (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
            .mapNotNull { entryOf(it["id"] as String) }
    }

    private suspend fun entryOf(id: String): ToolDataEntity? = AppDatabase.getDatabase(context).toolDataDao().getById(id)

    override suspend fun verbalize(operation: String, params: JSONObject, context: Context): String {
        val s = Strings.`for`(tool = "sequence", context = context)
        return if (operation in setOf(LOG_AFTER, IGNORE, IGNORE_ALL)) s.tool("verbalize_$operation") else s.shared("action_verbalize_unknown")
    }

    companion object {
        const val START = "start"
        const val LOG_AFTER = "log_after"
        const val IGNORE = "ignore"
        const val IGNORE_ALL = "ignore_all"
        const val DONE = "done"
        const val SKIP = "skip"
        const val BACK = "back"
        const val RESTART = "restart"
        const val PAUSE = "pause"
        const val RESUME = "resume"
        const val EXTEND = "extend"
        const val STOP = "stop"
        const val ADVANCE = "advance"
        const val RESUME_INTERRUPTED = "resume_interrupted"

        val GESTURES = setOf(DONE, SKIP, BACK, RESTART, PAUSE, RESUME, EXTEND, STOP, ADVANCE, RESUME_INTERRUPTED)

        fun statusOf(entry: ToolDataEntity): String? = entry.state?.let { JSONObject(it).optString(SequenceToolType.STATUS) }

        /** The run an entry keeps while its session runs, null otherwise. */
        fun runOf(entry: ToolDataEntity): SequenceRun? {
            val state = entry.state?.let { JSONObject(it) } ?: return null
            if (state.optString(SequenceToolType.STATUS) != SequenceToolType.Status.RUNNING) return null
            val run = state.optString(SequenceToolType.RUN).takeIf { it.isNotEmpty() } ?: return null
            return SequenceRun.fromJson(JSONObject(run))
        }
    }
}
