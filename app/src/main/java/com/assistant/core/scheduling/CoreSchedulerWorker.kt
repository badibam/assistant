package com.assistant.core.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.assistant.core.utils.LogManager

/**
 * WorkManager worker for centralized scheduling when app is closed.
 *
 * Runs periodically (every 15 minutes - WorkManager minimum) to trigger scheduler heartbeat.
 * Complements CoreScheduler's internal 1-minute heartbeat for app-open scenarios.
 *
 * Delegates all scheduling logic to CoreScheduler.tick() which handles:
 * - AI automations (via AIOrchestrator)
 * - Tool schedulers (via discovery pattern)
 *
 * Architecture:
 * - Lightweight worker, all logic in CoreScheduler
 * - No hardcoded dependencies on specific tools or AI
 * - Discovery pattern ensures extensibility
 */
class CoreSchedulerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        LogManager.service("CoreSchedulerWorker tick cycle started", "DEBUG")

        return try {
            // Delegate to CoreScheduler.tick()
            // tick() will:
            // 1. Call AIOrchestrator.tick() for AI automations
            // 2. Discover and call all tool schedulers via ToolTypeManager
            CoreScheduler.tick()

            LogManager.service("CoreSchedulerWorker tick cycle completed", "DEBUG")
            Result.success()

        } catch (e: Exception) {
            LogManager.service("CoreSchedulerWorker tick cycle failed: ${e.message}", "ERROR", e)
            Result.retry()
        }
    }
}
