package com.assistant.bench

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assistant.core.ai.data.MessageSegment
import com.assistant.core.ai.data.RichMessage
import com.assistant.core.ai.data.SessionType
import com.assistant.core.ai.domain.Phase
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.commands.CommandStatus
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.database.AppDatabase
import com.assistant.core.utils.AppConfigManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * One scenario of the bench (docs/design/local-models.md), played by the app itself: the base
 * emptied, the OpenAI-compatible provider configured from the arguments, the demo installed, then
 * a chat message sent or an automation run, through the real orchestrator, until the session
 * rests. What it leaves is judged elsewhere: the base is copied before and after
 * (files/bench/before.db, after.db), with how the session ended (files/bench/result.json), and
 * scripts/bench.py checks them. Nothing here knows a scenario.
 *
 * On the emulator only: it empties the app's base.
 *
 * Arguments (am instrument -e): base_url, api_key, model, output_forcing; kind ("chat" or
 * "automation"); message_b64 (the chat message, base64 UTF-8) or automation_id; timeout_minutes.
 */
@RunWith(AndroidJUnit4::class)
class BenchScenario {

    private val args = InstrumentationRegistry.getArguments()
    private fun arg(name: String): String = args.getString(name) ?: error("Missing instrumentation argument '$name'")

    @Test
    fun play() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val dir = File(context.filesDir, "bench").apply { deleteRecursively(); mkdirs() }
        val coordinator = Coordinator(context)

        prepare(context, coordinator)
        copyBase(context, File(dir, "before.db"))

        AIOrchestrator.initialize(context)
        val started = System.currentTimeMillis()
        val deadline = started + arg("timeout_minutes").toLong() * 60_000
        val outcome = when (val kind = arg("kind")) {
            "chat" -> playChat(String(Base64.decode(arg("message_b64"), Base64.DEFAULT), Charsets.UTF_8), deadline)
            "automation" -> playAutomation(context, arg("automation_id"), started, deadline)
            else -> error("Unknown kind '$kind'")
        }
        // A session still running is stopped, so the copy reads a base at rest
        if (outcome.status != "done") AIOrchestrator.stopActiveSession()
        delay(1_000)

        copyBase(context, File(dir, "after.db"))
        File(dir, "result.json").writeText(outcome.toJson(System.currentTimeMillis() - started).toString(2))
    }

    /** The base emptied, the provider configured and active, the demo installed over it. */
    private suspend fun prepare(context: Context, coordinator: Coordinator) {
        AppDatabase.getDatabase(context).clearAllTables()
        AppConfigManager.initialize(context)

        val config = JSONObject()
            .put("base_url", arg("base_url"))
            .put("api_key", arg("api_key"))
            .put("model", arg("model"))
            .put("output_forcing", arg("output_forcing"))
        succeed(coordinator.processUserAction("ai_provider_config.set", mapOf(
            "provider_id" to PROVIDER, "config" to config.keys().asSequence().associateWith { config.get(it) })), "provider config")
        succeed(coordinator.processUserAction("ai_provider_config.set_active", mapOf("provider_id" to PROVIDER)), "provider activation")
        // Installed after the provider: the demo's automations take the first configured one
        succeed(coordinator.processUserAction("demo.install", emptyMap()), "demo install")
    }

    private fun succeed(result: com.assistant.core.commands.CommandResult, what: String) {
        check(result.status == CommandStatus.SUCCESS) { "Bench setup failed at $what: ${result.error}" }
    }

    /** A consistent copy of the whole base, written ahead of its log included. */
    private fun copyBase(context: Context, file: File) {
        file.delete()
        AppDatabase.getDatabase(context).openHelper.writableDatabase.execSQL("VACUUM INTO '${file.absolutePath}'")
    }

    // ========================================================================================
    // Playing
    // ========================================================================================

    /** How a session ended for the bench, and what the bench answered on the way. */
    private class Outcome(
        val status: String,           // done, asked (a question to the user), timeout
        val validations: Int,
        val dataConfirmations: Int
    ) {
        fun toJson(elapsedMs: Long): JSONObject = JSONObject()
            .put("status", status)
            .put("validations", validations)
            .put("data_confirmations", dataConfirmations)
            .put("elapsed_ms", elapsedMs)
    }

    /** A new chat, the message sent, then waited on until the AI rests or asks the user. */
    private suspend fun playChat(message: String, deadline: Long): Outcome {
        AIOrchestrator.startNewChatSession()
        waitUntil(deadline) { AIOrchestrator.currentState.value.let { it.sessionId != null && it.sessionType == SessionType.CHAT && it.phase == Phase.IDLE } }
            ?: return Outcome("timeout", 0, 0)

        AIOrchestrator.sendMessage(RichMessage(listOf(MessageSegment.Text(message))))
        // The round begins, so that the idle phase awaited next is its end, not the moment before it
        waitUntil(deadline) { AIOrchestrator.currentState.value.phase != Phase.IDLE } ?: return Outcome("timeout", 0, 0)
        return answerUntilRest(deadline) { state -> state.phase == Phase.IDLE || state.sessionId == null }
    }

    /** The automation run now, waited on until its session ends. */
    private suspend fun playAutomation(context: Context, automationId: String, started: Long, deadline: Long): Outcome {
        AIOrchestrator.executeAutomation(automationId, started)
        val db = AppDatabase.getDatabase(context).openHelper.readableDatabase
        fun ended(): Boolean = db.query(
            "SELECT end_reason FROM ai_sessions WHERE automation_id = ? AND type = 'AUTOMATION' AND created_at >= ?",
            arrayOf<Any>(automationId, started)
        ).use { cursor -> cursor.moveToFirst() && !cursor.isNull(0) }
        return answerUntilRest(deadline) { ended() }
    }

    /**
     * Waits until [rested], approving what the AI asks to be approved — a validation, data above
     * the size threshold — and counting it. A question to the user ends the play: the bench has
     * no answer to give, and asking where the request was clear is what the scenario judges.
     */
    private suspend fun answerUntilRest(deadline: Long, rested: (com.assistant.core.ai.domain.AIState) -> Boolean): Outcome {
        var validations = 0
        var dataConfirmations = 0
        var restingSince: Long? = null
        while (System.currentTimeMillis() < deadline) {
            val state = AIOrchestrator.currentState.value
            when (state.phase) {
                Phase.WAITING_VALIDATION -> { validations++; AIOrchestrator.resumeWithValidation(true); delay(2_000) }
                Phase.WAITING_DATA_CONFIRMATION -> { dataConfirmations++; AIOrchestrator.resumeWithDataConfirmation(true); delay(2_000) }
                Phase.WAITING_COMMUNICATION_RESPONSE -> return Outcome("asked", validations, dataConfirmations)
                else -> {}
            }
            // At rest for a few seconds running: a continuation passes through no idle phase, but
            // the state may be read between two of its writes
            if (rested(AIOrchestrator.currentState.value)) {
                val since = restingSince ?: System.currentTimeMillis().also { restingSince = it }
                if (System.currentTimeMillis() - since >= REST_MS) return Outcome("done", validations, dataConfirmations)
            } else {
                restingSince = null
            }
            delay(POLL_MS)
        }
        return Outcome("timeout", validations, dataConfirmations)
    }

    private suspend fun waitUntil(deadline: Long, condition: () -> Boolean): Unit? {
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return Unit
            delay(POLL_MS)
        }
        return null
    }

    companion object {
        private const val PROVIDER = "compatible_standard"
        private const val POLL_MS = 250L
        private const val REST_MS = 3_000L
    }
}
