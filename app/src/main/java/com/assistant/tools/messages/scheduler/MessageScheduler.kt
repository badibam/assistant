package com.assistant.tools.messages.scheduler

import com.assistant.core.fields.settings.SettingValues
import com.assistant.core.utils.StoredSchedule
import com.assistant.tools.messages.MessageToolType
import com.assistant.core.tools.BaseSchemas
import android.content.Context
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.tools.ToolScheduler
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.AppConfigManager
import com.assistant.core.utils.DateTimeConverter
import com.assistant.core.utils.LogManager
import com.assistant.core.utils.ScheduleCalculator
import com.assistant.core.utils.ScheduleConfig
import com.assistant.core.validation.SchemaValidator
import kotlinx.serialization.json.Json
import com.assistant.core.utils.JsonUtils
import org.json.JSONObject
import java.time.ZoneId

/**
 * Scheduler for the Messages tool.
 *
 * One instance is one notification template. Its config holds the invariant part of every
 * send and the recurrence; its tool_data entries are the occurrences, one per send.
 *
 * Every tick does two things per instance, in this order:
 *
 * 1. Reconcile — bring the set of pending occurrences in line with the recurrence, within
 *    the creation horizon. Missing ones are created, orphans are deleted, the rest are left
 *    alone. There is deliberately no special path for "the recurrence just changed": editing
 *    it simply gives the next reconciliation more to do.
 *
 * 2. Fire — resolve every pending occurrence whose time has come, oldest first. It is sent,
 *    or expired if it is later than the template allows, or cancelled if the template was
 *    suspended in the meantime.
 *
 * The invariant part of the message is copied onto the occurrence AT SEND TIME and never at
 * creation, so a pending occurrence is an intention rather than a half-written event, and
 * editing the template reaches everything that has not gone out yet.
 */
object MessageScheduler : ToolScheduler {


    /** Guards against a pattern that would otherwise enumerate forever within the horizon. */
    private const val MAX_EXPECTED_PER_HORIZON = 1000

    /**
     * One pass at a time. Two passes that overlap (the core scheduler's tick, a manual send)
     * would both read the same pending set and both create what is missing. A pass that finds
     * one running leaves: that one does the work, and what it did not see goes to the next
     * tick. It never waits, since a manual send calls it from inside a coordinator operation
     * that the running pass may be waiting on.
     */
    private val passLock = kotlinx.coroutines.sync.Mutex()

    /**
     * One pending occurrence as the scheduler needs it: its identity, when it is due, where
     * it came from, and the part already written into it.
     */
    private data class PendingOccurrence(
        val id: String,
        val dueAt: Long,
        val triggeredBy: String,
        val data: JSONObject
    )

    override suspend fun checkScheduled(context: Context) {
        if (!passLock.tryLock()) {
            LogManager.service("MessageScheduler: a pass is already running, this one leaves it the work", "DEBUG")
            return
        }
        try {
            val coordinator = Coordinator(context)
            val now = System.currentTimeMillis()

            // include_config is required: without it list_all returns a minimal snapshot with no
            // config_json, and the config IS the template here — there would be nothing to read.
            val instancesResult = coordinator.processUserAction(
                "tools.list_all",
                mapOf("include_config" to true)
            )
            if (!instancesResult.isSuccess) {
                LogManager.service("Failed to list tool instances: ${instancesResult.error}", "ERROR")
                return
            }

            @Suppress("UNCHECKED_CAST")
            val instances = (instancesResult.data?.get("tool_instances") as? List<Map<String, Any>>) ?: emptyList()
            val messageInstances = instances.filter { it["tooltype"] == "messages" }

            if (messageInstances.isEmpty()) return


            for (instance in messageInstances) {
                val toolInstanceId = instance["id"] as? String ?: continue
                try {
                    processInstance(context, coordinator, toolInstanceId, instance, now)
                } catch (e: Exception) {
                    // One broken template must not stop the others
                    LogManager.service("Failed to process message template $toolInstanceId: ${e.message}", "ERROR", e)
                }
            }


        } catch (e: Exception) {
            LogManager.service("MessageScheduler.checkScheduled() failed: ${e.message}", "ERROR", e)
        } finally {
            passLock.unlock()
        }
    }

    // ========================================
    // Per-instance pass
    // ========================================

    private suspend fun processInstance(
        context: Context,
        coordinator: Coordinator,
        toolInstanceId: String,
        instance: Map<String, Any>,
        now: Long
    ) {
        val configJson = (instance["config"] as? Map<String, Any?>)?.let { JsonUtils.toJSONObject(it) }
        if (configJson == null) {
            LogManager.service("Message template $toolInstanceId has no config, skipping", "WARN")
            return
        }
        val config = configJson
        val timezone = AppConfigManager.getDateTimeConfig().getZoneId()

        val stored = StoredSchedule.of(config)
        // The switch sits at the root of the config, not inside the recurrence: it suspends
        // the whole template — everything the instance owes, a hand-placed occurrence
        // included — and it exists even when there is no recurrence at all.
        val settings = com.assistant.core.tools.ToolConfigSettings.read(MessageToolType, config, context)
        val enabled = settings.boolean("enabled")

        // Not read, the pending set is unknown: taken for empty, every pass would create again
        // what already exists. Nothing is created, deleted or fired until it reads.
        val pending = loadPending(coordinator, toolInstanceId, timezone) ?: return

        // Suspended means create nothing and delete nothing: the pending set drains on its own
        // as each occurrence is cancelled at its time, so suspending is never destructive.
        // Live, it reconciles even with no recurrence at all — nothing is then expected, so
        // removing the recurrence sheds what it had generated, exactly like changing it.
        //
        // An unreadable recurrence skips reconciliation: read as none, it would delete every
        // pending occurrence. What is already pending still goes out at its time. The Messages
        // screen shows the same failure, so it is not left to this log line alone.
        when (stored) {
            is StoredSchedule.Unreadable -> LogManager.service(
                "Message template $toolInstanceId: recurrence unreadable, nothing created or deleted (${stored.cause})",
                "ERROR"
            )
            is StoredSchedule.Readable -> if (enabled) {
                reconcilePending(context, coordinator, toolInstanceId, settings, stored.schedule, pending, now, timezone)
            }
            StoredSchedule.None -> if (enabled) {
                reconcilePending(context, coordinator, toolInstanceId, settings, null, pending, now, timezone)
            }
        }

        // Reconciliation only ever touches occurrences still in the future, so the due set is
        // exactly what was loaded above — nothing it did can have added to or removed from it.
        firePending(coordinator, toolInstanceId, settings, enabled, pending, now)
    }

    // ========================================
    // 1. Reconciliation
    // ========================================

    /**
     * Brings the pending set in line with the recurrence, inside the creation horizon.
     *
     * Only occurrences born of the recurrence (triggered_by = SCHEDULE) are considered: one
     * placed by hand or by the AI is not the recurrence's to delete.
     *
     * Only the future is touched. An occurrence already due is resolved by firePending, which
     * either sends it or expires it — deleting it here would silently drop something that was
     * legitimately due before the template changed.
     */
    private suspend fun reconcilePending(
        context: Context,
        coordinator: Coordinator,
        toolInstanceId: String,
        settings: SettingValues,
        schedule: ScheduleConfig?,
        pending: List<PendingOccurrence>,
        now: Long,
        timezone: ZoneId
    ) {
        val horizon = settings.number("creation_horizon")?.toLong() ?: 0L
        if (horizon <= 0) {
            LogManager.service("Message template $toolInstanceId has no creation horizon, skipping reconciliation", "WARN")
            return
        }
        val horizonEnd = now + horizon

        // No recurrence means nothing is expected, so everything it had generated is orphaned:
        // removing the recurrence and changing it take the same path.
        val expected = if (schedule == null) emptyList() else expectedOccurrences(schedule, now, horizonEnd, toolInstanceId)

        val futureScheduled = pending.filter { it.dueAt > now && it.triggeredBy == "SCHEDULE" }
        val existingTimes = futureScheduled.map { it.dueAt }.toSet()
        val expectedTimes = expected.toSet()

        // The recurrence owes one occurrence per time: a second one at the same time is a
        // duplicate, the first kept
        val duplicates = futureScheduled.groupBy { it.dueAt }.values.flatMap { it.drop(1) }
        val orphans = futureScheduled.filter { it.dueAt !in expectedTimes } + duplicates

        for (orphan in orphans.distinctBy { it.id }) {
            val result = coordinator.processUserAction("tool_data.delete", mapOf("id" to orphan.id))
            if (result.isSuccess) {
                LogManager.service("Deleted occurrence ${orphan.id}: no longer matches the recurrence, or duplicates another", "INFO")
            } else {
                LogManager.service("Failed to delete orphaned occurrence ${orphan.id}: ${result.error}", "ERROR")
            }
        }

        for (dueAt in expected.filter { it !in existingTimes }) {
            createPendingOccurrence(context, coordinator, toolInstanceId, settings, dueAt, timezone)
        }
    }

    /**
     * Enumerates the times the recurrence produces between now and the end of the horizon.
     *
     * ScheduleCalculator answers "next execution strictly after this instant", so walking it
     * forward gives the whole set. The iteration cap and the non-progress check are guards, not
     * fallbacks: if either triggers, the pattern is wrong and the log says so.
     */
    private fun expectedOccurrences(
        schedule: ScheduleConfig,
        now: Long,
        horizonEnd: Long,
        toolInstanceId: String
    ): List<Long> {
        val times = mutableListOf<Long>()
        var cursor = now

        while (times.size < MAX_EXPECTED_PER_HORIZON) {
            val next = ScheduleCalculator.calculateNextExecution(
                pattern = schedule.pattern,
                fromTimestamp = cursor
            ) ?: break

            if (next > horizonEnd) break

            if (next <= cursor) {
                LogManager.service(
                    "Schedule pattern of $toolInstanceId did not advance past $cursor, stopping enumeration",
                    "ERROR"
                )
                break
            }

            times.add(next)
            cursor = next
        }

        if (times.size >= MAX_EXPECTED_PER_HORIZON) {
            LogManager.service(
                "Schedule pattern of $toolInstanceId produced $MAX_EXPECTED_PER_HORIZON occurrences within its horizon, truncated",
                "ERROR"
            )
        }

        return times
    }

    /**
     * Creates one pending occurrence, due at the given time and carrying nothing else.
     *
     * It holds only its status and its origin. The invariant part of the message is not copied
     * in here — that happens at send time, so that editing the template still reaches it.
     *
     * Validated before insertion: ToolDataService.create does not validate on its own, and the
     * occurrences this replaces were written with no validation at all.
     */
    private suspend fun createPendingOccurrence(
        context: Context,
        coordinator: Coordinator,
        toolInstanceId: String,
        settings: SettingValues,
        dueAt: Long,
        timezone: ZoneId
    ) {
        val name = settings.string("name")!!
        val state = JSONObject().apply {
            put("status", "pending")
            put("triggered_by", "SCHEDULE")
        }

        val validation = validateOccurrence(context, toolInstanceId, name, dueAt, JSONObject(), state)
        if (validation != null) {
            LogManager.service("Refusing to create occurrence for $toolInstanceId: $validation", "ERROR")
            return
        }

        val result = coordinator.processUserAction("tool_data.create", mapOf(
            "tool_instance_id" to toolInstanceId,
            "tooltype" to "messages",
            "name" to name,
            "timestamp" to dueAt,
            "data" to JSONObject(),
            "state" to state
        ))

        if (result.isSuccess) {
            LogManager.service("Created pending occurrence for $toolInstanceId due at ${DateTimeConverter.timestampToISO(dueAt, timezone)}", "INFO")
        } else {
            LogManager.service("Failed to create pending occurrence for $toolInstanceId: ${result.error}", "ERROR")
        }
    }

    // ========================================
    // 2. Firing
    // ========================================

    /**
     * Resolves every pending occurrence whose time has come, oldest first.
     *
     * Three outcomes, and each says why it happened rather than leaving it to be guessed later:
     * - cancelled: the template was suspended before its time came. A decision, not a miss.
     *   The switch covers everything the instance owes, a hand-placed occurrence included.
     * - expired: it came due longer ago than validity_window_minutes allows. A morning reminder
     *   arriving mid-afternoon is worse than no reminder.
     * - sent: the notification goes out, and the invariant part of the message is copied in.
     *
     * An occurrence with nothing in it yet is a fourth case and gets no outcome at all: it is
     * left pending, so it can still be filled, and the validity window resolves it if nobody
     * does. Naming that expired would say "too late" about something that was never written.
     */
    private suspend fun firePending(
        coordinator: Coordinator,
        toolInstanceId: String,
        settings: SettingValues,
        enabled: Boolean,
        pending: List<PendingOccurrence>,
        now: Long
    ) {
        val due = pending.filter { it.dueAt <= now }.sortedBy { it.dueAt }
        if (due.isEmpty()) return

        val validityWindowMillis = settings.number("validity_window")?.toLong()
            ?: error("Message template $toolInstanceId has no validity window, not even its default")

        for (occurrence in due) {
            when {
                !enabled ->
                    resolveWithoutSending(coordinator, occurrence, "cancelled")

                now - occurrence.dueAt > validityWindowMillis ->
                    resolveWithoutSending(coordinator, occurrence, "expired")

                else ->
                    send(coordinator, toolInstanceId, settings, occurrence)
            }
        }
    }

    /**
     * Sends the notification and marks the occurrence sent.
     *
     * Composition: the common title and the title written for this send are joined, and so are
     * the common body and the body written for this send. A missing part contributes nothing —
     * there is no value stepping in for another. A reminder whose text never varies therefore
     * needs nothing written per send: its common part alone goes out.
     */
    private suspend fun send(
        coordinator: Coordinator,
        toolInstanceId: String,
        settings: SettingValues,
        occurrence: PendingOccurrence
    ) {
        val commonTitle = settings.string("common_title")?.takeIf { it.isNotEmpty() }
        val commonContent = settings.string("common_content")?.takeIf { it.isNotEmpty() }
        val ownTitle = occurrence.data.optString("title").takeIf { it.isNotEmpty() }
        val ownContent = occurrence.data.optString("content").takeIf { it.isNotEmpty() }
        val priority = settings.string("priority")!!

        val title = listOfNotNull(commonTitle, ownTitle).joinToString(" · ")
        val content = listOfNotNull(commonContent, ownContent).joinToString("\n\n").takeIf { it.isNotEmpty() }

        if (title.isEmpty() && content == null) {
            // Nothing anywhere: the template says nothing of its own and nobody wrote anything
            // for this send. A fixed reminder does not land here — its common part is enough on
            // its own — so reaching this means the template has no text at all. Nothing goes
            // out, and the occurrence stays pending until its validity window expires it.
            LogManager.service("Occurrence ${occurrence.id} has nothing to show, not sending", "DEBUG")
            return
        }

        var notificationSent = false
        if (settings.boolean("external_notifications")) {
            val params = mutableMapOf<String, Any>(
                "title" to title,
                "priority" to priority
            )
            if (content != null) params["content"] = content

            val result = coordinator.processUserAction("notifications.send", params)
            notificationSent = result.isSuccess
            if (!notificationSent) {
                LogManager.service("Notification failed for occurrence ${occurrence.id}: ${result.error}", "WARN")
            }
        } else {
            LogManager.service("External notifications disabled for $toolInstanceId, occurrence recorded without one", "DEBUG")
        }

        // What went out is copied into the occurrence, which keeps saying it when the template changes
        val data = JSONObject().apply {
            if (commonTitle != null) put("common_title", commonTitle)
            if (commonContent != null) put("common_content", commonContent)
            put("priority", priority)
        }
        val state = JSONObject().apply {
            put("status", "sent")
            put("notification_sent", notificationSent)
            put("read", false)
            put("archived", false)
        }

        updateOccurrence(coordinator, occurrence, data, state, "sent")
    }

    /** Marks an occurrence resolved without a notification, keeping the part already written. */
    private suspend fun resolveWithoutSending(
        coordinator: Coordinator,
        occurrence: PendingOccurrence,
        status: String
    ) {
        updateOccurrence(coordinator, occurrence, JSONObject(), JSONObject().put("status", status), status)
    }

    private suspend fun updateOccurrence(
        coordinator: Coordinator,
        occurrence: PendingOccurrence,
        data: JSONObject,
        state: JSONObject,
        status: String
    ) {
        val result = coordinator.processUserAction("tool_data.update", mapOf(
            "id" to occurrence.id,
            "data" to data,
            "state" to state
        ))

        if (result.isSuccess) {
            LogManager.service("Occurrence ${occurrence.id} resolved as $status", "INFO")
        } else {
            LogManager.service("Failed to mark occurrence ${occurrence.id} as $status: ${result.error}", "ERROR")
        }
    }

    // ========================================
    // Reading and validation helpers
    // ========================================

    /**
     * Loads every pending occurrence of an instance, with no time bound; null when they
     * cannot be read.
     *
     * The status filter is what makes the absence of a bound possible. Asking by time window
     * instead would lose any occurrence left behind by a gap longer than the window — the app
     * unopened for a week — and leave it pending forever with nothing to notice it.
     */
    private suspend fun loadPending(
        coordinator: Coordinator,
        toolInstanceId: String,
        timezone: ZoneId
    ): List<PendingOccurrence>? {
        val result = coordinator.processUserAction("tool_data.get", mapOf(
            "tool_instance_id" to toolInstanceId,
            "filters" to listOf(mapOf("field" to "state.status", "op" to "in", "value" to listOf("pending")))
        ))

        if (!result.isSuccess) {
            LogManager.service("Failed to load pending occurrences of $toolInstanceId, nothing done for it: ${result.error}", "ERROR")
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val entries = (result.data?.get("entries") as? List<Map<String, Any>>) ?: emptyList()

        return entries.mapNotNull { entry ->
            val id = entry["id"] as? String ?: return@mapNotNull null
            val dueAtMillis = (entry["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null
            val dataMap = entry["data"] as? Map<*, *> ?: return@mapNotNull null
            val stateMap = entry["state"] as? Map<*, *> ?: emptyMap<Any, Any>()

            try {
                val data = JsonUtils.toJSONObject(dataMap.entries.associate { (k, v) -> k.toString() to v })
                val state = JsonUtils.toJSONObject(stateMap.entries.associate { (k, v) -> k.toString() to v })
                PendingOccurrence(
                    id = id,
                    dueAt = dueAtMillis,
                    triggeredBy = state.optString("triggered_by", "MANUAL"),
                    data = data
                )
            } catch (e: Exception) {
                LogManager.service("Unreadable pending occurrence $id, skipped: ${e.message}", "ERROR", e)
                null
            }
        }
    }

    /**
     * Validates an occurrence against the instance's data schema.
     * @return null when valid, the error message otherwise.
     */
    private fun validateOccurrence(
        context: Context,
        toolInstanceId: String,
        name: String,
        dueAt: Long,
        data: JSONObject,
        state: JSONObject
    ): String? {
        val toolType = ToolTypeManager.getToolType("messages") ?: return "messages tooltype not found"
        val schema = try {
            BaseSchemas.entrySchema(toolType, toolInstanceId, context)
        } catch (e: IllegalStateException) {
            // The tool's custom fields cannot be read: the occurrence cannot be checked
            return e.message ?: "messages_data schema unavailable"
        }

        val entry = mapOf(
            "tool_instance_id" to toolInstanceId,
            "tooltype" to "messages",
            "name" to name,
            "timestamp" to dueAt,
            "data" to data,
            "state" to state
        )

        val validation = SchemaValidator.validate(schema, entry, context)
        return if (validation.isValid) null else (validation.errorMessage ?: "invalid occurrence")
    }
}
