package com.assistant.tools.goal

import android.content.Context
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.strings.Strings
import com.assistant.core.tools.ToolScheduler
import com.assistant.core.utils.JsonUtils
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.ScheduleCalculator
import com.assistant.core.utils.StoredSchedule
import org.json.JSONObject

/**
 * The life of goals' attempts, at each tick (docs/design/missing-tools.md, « Objectif »):
 *
 * 1. An attempt whose period ended is to be validated: its verdict waits to be confirmed, so a
 *    criterion forgotten is caught up rather than turned into a false failure. One notification
 *    tells it, once, unless the goal says not to.
 * 2. An attempt left to validate past the goal's delay expires, which is not a failure.
 * 3. A goal switched on opens its attempt: a one-off goal once, from its start; a recurring one at
 *    the last occurrence of its schedule that came, the ones in between not caught up. Its period
 *    lasts the goal's duration, or until the next occurrence. An attempt to validate does not hold
 *    the next one back: periods would slide.
 */
object GoalScheduler : ToolScheduler {

    /** Guards the walk through a schedule's occurrences. */
    private const val MAX_STEPS = 10_000

    private val passLock = kotlinx.coroutines.sync.Mutex()

    override suspend fun checkScheduled(context: Context) {
        if (!passLock.tryLock()) return
        try {
            val coordinator = Coordinator(context)
            val tools = coordinator.processUserAction("tools.list_all", mapOf("include_config" to true))
            if (!tools.isSuccess) {
                LogManager.service("GoalScheduler: tools not read: ${tools.error}", "ERROR")
                return
            }
            (tools.data?.get("tool_instances") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
                .filter { it["tooltype"] == "goal" }
                .forEach { tool ->
                    try {
                        pass(context, coordinator, tool)
                    } catch (e: Exception) {
                        LogManager.service("GoalScheduler: goal ${tool["id"]} not handled: ${e.message}", "ERROR", e)
                    }
                }
        } finally {
            passLock.unlock()
        }
    }

    private suspend fun pass(context: Context, coordinator: Coordinator, tool: Map<*, *>) {
        val id = tool["id"] as String
        @Suppress("UNCHECKED_CAST")
        val config = JsonUtils.toJSONObject(tool["config"] as Map<String, Any?>)
        val now = System.currentTimeMillis()
        val attempts = attemptsOf(coordinator, id)
        val s = Strings.`for`(tool = "goal", context = context)

        // 1 and 2: periods ended, delays passed
        val expiry = (config.opt("expiry_delay") as? Number)?.toLong() ?: GoalToolType.DEFAULT_EXPIRY
        for (attempt in attempts) {
            val end = (attempt.state.opt(GoalToolType.PERIOD_END) as? Number)?.toLong() ?: continue
            when (attempt.state.optString(GoalToolType.STATUS)) {
                GoalToolType.Status.ACTIVE -> if (end <= now) {
                    setState(coordinator, attempt.id, mapOf(GoalToolType.STATUS to GoalToolType.Status.TO_VALIDATE))
                    if (config.optBoolean("notify", true) && !attempt.state.optBoolean(GoalToolType.NOTIFIED)) {
                        coordinator.processUserAction("notifications.send", mapOf(
                            "title" to (config.optString("name").ifEmpty { s.tool("display_name") }),
                            "content" to s.tool("notification_to_validate"),
                            "priority" to "default"
                        ))
                        setState(coordinator, attempt.id, mapOf(GoalToolType.NOTIFIED to true))
                    }
                }
                GoalToolType.Status.TO_VALIDATE -> if (end + expiry <= now) {
                    setState(coordinator, attempt.id, mapOf(GoalToolType.STATUS to GoalToolType.Status.EXPIRED))
                }
            }
        }

        // 3: the attempt to open, if any
        if (!config.optBoolean("enabled", true)) return
        val duration = (config.opt("duration") as? Number)?.toLong()
        val opening: Pair<Long, Long?>? = when (val stored = StoredSchedule.of(config)) {
            is StoredSchedule.Unreadable -> {
                LogManager.service("GoalScheduler: schedule of goal $id unreadable (${stored.cause}), nothing opened", "ERROR")
                null
            }
            StoredSchedule.None -> {
                val start = (config.opt("start") as? Number)?.toLong() ?: ((tool["created_at"] as? Number)?.toLong() ?: now)
                if (attempts.isEmpty() && start <= now) start to ((config.opt("deadline") as? Number)?.toLong() ?: duration?.let { start + it })
                else null
            }
            is StoredSchedule.Readable -> {
                val schedule = stored.schedule
                val latest = attempts.maxOfOrNull { it.start }
                // The last occurrence that came since the last attempt (since the goal was made, for the first)
                var cursor = latest ?: ((tool["created_at"] as? Number)?.toLong() ?: now)
                var due: Long? = null
                var steps = 0
                while (steps++ < MAX_STEPS) {
                    val next = ScheduleCalculator.calculateNextExecution(schedule.pattern, cursor) ?: break
                    if (next > now || next <= cursor) break
                    due = next
                    cursor = next
                }
                due?.takeIf { d -> attempts.none { it.start == d } }?.let { start ->
                    val end = duration?.let { start + it }
                        ?: ScheduleCalculator.calculateNextExecution(schedule.pattern, start)?.let { it - 1 }
                    start to end
                }
            }
        }
        opening?.let { (start, end) ->
            // One active attempt at a time: the one before goes to be validated
            attempts.filter { it.state.optString(GoalToolType.STATUS) == GoalToolType.Status.ACTIVE }.forEach {
                setState(coordinator, it.id, mapOf(GoalToolType.STATUS to GoalToolType.Status.TO_VALIDATE))
            }
            val created = coordinator.processUserAction("tool_data.create", mapOf(
                "tool_instance_id" to id,
                "name" to (config.optString("name").ifEmpty { s.tool("display_name") }),
                "timestamp" to start,
                "data" to mapOf(GoalToolType.DEFINITION to GoalDefinition.copyOf(config).toString()),
                "state" to buildMap {
                    put(GoalToolType.STATUS, GoalToolType.Status.ACTIVE)
                    end?.let { put(GoalToolType.PERIOD_END, it) }
                }
            ))
            if (!created.isSuccess) LogManager.service("GoalScheduler: attempt of goal $id not opened: ${created.error}", "ERROR")
        }
    }

    private data class AttemptState(val id: String, val start: Long, val state: JSONObject)

    private suspend fun attemptsOf(coordinator: Coordinator, toolInstanceId: String): List<AttemptState> {
        val result = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to toolInstanceId, "fields" to listOf("id", "timestamp", "state")))
        if (!result.isSuccess) throw IllegalStateException(result.error ?: "tool_data.get")
        return (result.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>().map { e ->
            @Suppress("UNCHECKED_CAST")
            AttemptState(e["id"] as String, (e["timestamp"] as Number).toLong(), (e["state"] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) } ?: JSONObject())
        }
    }

    private suspend fun setState(coordinator: Coordinator, id: String, state: Map<String, Any>) {
        val result = coordinator.processUserAction("tool_data.update", mapOf("id" to id, "state" to state))
        if (!result.isSuccess) LogManager.service("GoalScheduler: attempt $id not updated: ${result.error}", "ERROR")
    }
}
