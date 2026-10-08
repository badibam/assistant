package app.treelune.core.scheduling

import android.content.Context
import app.treelune.core.ai.orchestration.AIOrchestrator
import app.treelune.core.tools.ToolTypeManager
import app.treelune.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Centralized scheduler for all periodic background tasks.
 *
 * Architecture: Single point of entry for scheduling across the app
 * - AI automations (via AIOrchestrator)
 * - Tool schedulers (via discovery pattern)
 *
 * Heartbeat mechanism:
 * - 1 minute coroutine (app-open, high reactivity)
 * - 10 minute exact alarm (SchedulerAlarm), which also rings while the phone sleeps
 *
 * Discovery pattern:
 * - No hardcoded dependencies on specific tools
 * - Tools register schedulers via ToolTypeContract.getScheduler()
 * - CoreScheduler discovers and calls all registered schedulers
 *
 * Lifecycle:
 * - attach() called from TreeluneApplication.onCreate(), whatever starts the process
 * - initialize() called from MainActivity.onCreate(), for the heartbeat
 * - shutdown() called from MainActivity.onDestroy()
 * - tick() called periodically by internal heartbeat + SchedulerAlarm
 */
object CoreScheduler {

    private lateinit var context: Context
    private val schedulerScope = CoroutineScope(Dispatchers.Default)
    private var heartbeatJob: Job? = null

    /**
     * Gives the scheduler the app's context, before any tick: the process may be started by the
     * alarm alone, with no screen to do it.
     */
    fun attach(appContext: Context) {
        context = appContext.applicationContext
    }

    /**
     * Starts the internal 1-minute heartbeat for app-open scenarios.
     *
     * @param appContext Application context
     */
    fun initialize(appContext: Context) {
        attach(appContext)
        startHeartbeat()
        LogManager.service("CoreScheduler initialized with 1-minute internal heartbeat", "INFO")
    }

    /**
     * Start internal heartbeat coroutine.
     * Ticks every 1 minute when app is open for better reactivity.
     */
    private fun startHeartbeat() {
        // Cancel existing heartbeat if any
        heartbeatJob?.cancel()

        heartbeatJob = schedulerScope.launch {
            LogManager.service("CoreScheduler: Starting 1-minute heartbeat", "INFO")

            while (true) {
                try {
                    // Wait 1 minute
                    delay(60_000L)

                    tick()

                } catch (e: kotlinx.coroutines.CancellationException) {
                    LogManager.service("CoreScheduler: Heartbeat cancelled", "INFO")
                    throw e // Re-throw to stop the loop
                } catch (e: Exception) {
                    LogManager.service("CoreScheduler: Heartbeat error: ${e.message}", "ERROR", e)
                    // Continue despite error
                }
            }
        }
    }

    /**
     * Main tick function called periodically.
     *
     * Execution order:
     * 1. AI scheduling (core functionality, high priority)
     * 2. Tool scheduling (discovery via ToolTypeManager)
     *
     * Error handling: Each component logs errors independently,
     * one failure doesn't block others.
     */
    suspend fun tick() = kotlinx.coroutines.withContext(app.treelune.core.coordinator.Origin(app.treelune.core.coordinator.Source.SCHEDULER)) {
        // Everything a tick starts is the scheduler's, unless it is the AI's (processAICommand)

        try {
            // 1. AI scheduling (AIOrchestrator handles automations + session management), set up
            // here when the alarm started the process and no screen did
            AIOrchestrator.initialize(context)
            AIOrchestrator.tick()

        } catch (e: Exception) {
            LogManager.service("CoreScheduler: AI scheduling error: ${e.message}", "ERROR", e)
            // Continue to tool scheduling despite AI error
        }

        try {
            // 2. Tool scheduling (discovery pattern)

            val allTools = ToolTypeManager.getAllToolTypes()

            allTools.forEach { (toolTypeName, toolType) ->
                try {
                    val scheduler = toolType.getScheduler()
                    if (scheduler != null) {
                        scheduler.checkScheduled(context)
                    }
                } catch (e: Exception) {
                    LogManager.service(
                        "CoreScheduler: Error in scheduler for tool type '$toolTypeName': ${e.message}",
                        "ERROR",
                        e
                    )
                    // Continue to next tool despite error
                }
            }

        } catch (e: Exception) {
            LogManager.service("CoreScheduler: Tool scheduling error: ${e.message}", "ERROR", e)
        }

    }

    /**
     * Shutdown the scheduler and cleanup resources.
     * Cancels the internal heartbeat coroutine.
     */
    fun shutdown() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        LogManager.service("CoreScheduler shutdown", "INFO")
    }
}
