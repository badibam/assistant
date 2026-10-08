package app.treelune.tools.sequence

import android.content.Context
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess
import app.treelune.core.strings.Strings
import app.treelune.core.tools.ToolScheduler
import app.treelune.core.utils.JsonUtils
import app.treelune.core.utils.LogManager
import app.treelune.core.utils.ScheduleCalculator
import app.treelune.core.utils.StoredSchedule

/**
 * The planned sessions (docs/design/sequence-tool.md, « Le planning »), as a questionnaire's
 * invitations: at each time its schedule gives, one entry « planned », dated from that time, and
 * one notification. The times that came while the app did not run are created too, for « Ignore
 * all » on the way back from an absence, the notification only for the last one. A planned
 * session never done stays planned: « missed » is read from it.
 */
object SequenceScheduler : ToolScheduler {

    /** At most this many entries created for one session in one pass. */
    private const val MAX_PER_PASS = 60

    /** Guards the walk through a schedule's times. */
    private const val MAX_STEPS = 20_000

    private val passLock = kotlinx.coroutines.sync.Mutex()

    override suspend fun checkScheduled(context: Context) {
        if (!passLock.tryLock()) return
        try {
            val coordinator = Coordinator(context)
            val tools = coordinator.processUserAction("tools.list_all", mapOf("include_config" to true))
            if (!tools.isSuccess) {
                LogManager.service("SequenceScheduler: tools not read: ${tools.error}", "ERROR")
                return
            }
            (tools.data?.get("tool_instances") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
                .filter { it["tooltype"] == "sequence" }
                .forEach { tool ->
                    try {
                        pass(context, coordinator, tool)
                    } catch (e: Exception) {
                        LogManager.service("SequenceScheduler: session ${tool["id"]} not handled: ${e.message}", "ERROR", e)
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
        if (!config.optBoolean("enabled", true)) return
        val schedule = when (val stored = StoredSchedule.of(config)) {
            StoredSchedule.None -> return
            is StoredSchedule.Unreadable -> {
                LogManager.service("SequenceScheduler: schedule of $id unreadable (${stored.cause}), nothing created", "ERROR")
                return
            }
            is StoredSchedule.Readable -> stored.schedule
        }
        val entries = coordinator.processUserAction("tool_data.get", mapOf("tool_instance_id" to id))
        if (!entries.isSuccess) throw IllegalStateException(entries.error ?: "tool_data.get")
        val dated = (entries.data?.get("entries") as? List<*> ?: emptyList<Any>()).filterIsInstance<Map<*, *>>()
            .mapNotNull { (it["timestamp"] as? Number)?.toLong() }.toSet()
        // Every time the schedule gave since the session was made, without its entry yet; a
        // session started on demand is dated from its start, which no time of the schedule matches
        val now = System.currentTimeMillis()
        var cursor = (tool["created_at"] as? Number)?.toLong() ?: now
        val times = mutableListOf<Long>()
        var steps = 0
        while (steps++ < MAX_STEPS) {
            val next = ScheduleCalculator.calculateNextExecution(schedule.pattern, cursor) ?: break
            if (next > now || next <= cursor) break
            times.add(next)
            cursor = next
        }
        // Only the times after the last one already given its entry: those before were created
        // or left out on purpose, the oldest when more than MAX_PER_PASS came at once
        val lastGiven = times.filter { it in dated }.maxOrNull()
        val due = times.filter { it !in dated && (lastGiven == null || it > lastGiven) }.takeLast(MAX_PER_PASS)
        due.forEach { time ->
            val created = coordinator.processUserAction("tool_data.create", mapOf(
                "tool_instance_id" to id,
                "timestamp" to time,
                "data" to emptyMap<String, Any>(),
                "state" to mapOf(SequenceToolType.STATUS to SequenceToolType.Status.PLANNED)
            ))
            if (!created.isSuccess) LogManager.service("SequenceScheduler: entry of $id at $time not created: ${created.error}", "ERROR")
        }
        if (due.isNotEmpty()) {
            val s = Strings.`for`(tool = "sequence", context = context)
            coordinator.processUserAction("notifications.send", mapOf(
                "title" to (config.optString("name").ifEmpty { s.tool("display_name") }),
                "content" to s.tool("notification_planned"),
                "priority" to "default",
                "icon_name" to config.optString("icon_name").ifEmpty { SequenceToolType.getDefaultIconName() },
                "tool_instance_id" to id
            ))
        }
    }
}
