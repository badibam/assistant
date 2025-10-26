package com.assistant.core.scheduling

import android.content.Context
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.tools.ToolTypeManager
import com.assistant.core.utils.LogManager
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
 * - 15 minute WorkManager (app-closed, background)
 *
 * Discovery pattern:
 * - No hardcoded dependencies on specific tools
 * - Tools register schedulers via ToolTypeContract.getScheduler()
 * - CoreScheduler discovers and calls all registered schedulers
 *
 * Lifecycle:
 * - initialize() called from MainActivity.onCreate()
 * - shutdown() called from MainActivity.onDestroy()
 * - tick() called periodically by internal heartbeat + WorkManager
 */
object CoreScheduler {

    private lateinit var context: Context
    private val schedulerScope = CoroutineScope(Dispatchers.Default)
    private var heartbeatJob: Job? = null

    /**
     * Initialize the scheduler with app context.
     * Starts the internal 1-minute heartbeat for app-open scenarios.
     *
     * @param appContext Application context
     */
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
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

                    // Trigger tick
                    LogManager.service("CoreScheduler: Heartbeat tick (1 min)", "DEBUG")
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
    suspend fun tick() {
        LogManager.service("CoreScheduler.tick() started", "DEBUG")

        try {
            // 1. AI scheduling (AIOrchestrator handles automations + session management)
            LogManager.service("CoreScheduler: Calling AIOrchestrator.tick()", "DEBUG")
            AIOrchestrator.tick()

        } catch (e: Exception) {
            LogManager.service("CoreScheduler: AI scheduling error: ${e.message}", "ERROR", e)
            // Continue to tool scheduling despite AI error
        }

        try {
            // 2. Tool scheduling (discovery pattern)
            LogManager.service("CoreScheduler: Checking tool schedulers", "DEBUG")

            val allTools = ToolTypeManager.getAllToolTypes()
            LogManager.service("CoreScheduler: Found ${allTools.size} tool types", "DEBUG")

            allTools.forEach { (toolTypeName, toolType) ->
                try {
                    val scheduler = toolType.getScheduler()
                    if (scheduler != null) {
                        LogManager.service("CoreScheduler: Calling scheduler for tool type '$toolTypeName'", "DEBUG")
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

        LogManager.service("CoreScheduler.tick() completed", "DEBUG")
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
