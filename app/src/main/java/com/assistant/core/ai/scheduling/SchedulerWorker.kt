package com.assistant.core.ai.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager

/**
 * WorkManager worker for scheduling automation executions when app is closed
 *
 * Runs periodically (every 15 minutes - WorkManager minimum) to trigger scheduler heartbeat
 * Complements AIOrchestrator's internal 1-minute heartbeat for app-open scenarios
 *
 * Delegates all scheduling logic to AIOrchestrator.tick() (V2 event-driven architecture)
 *
 * Architecture V2:
 * - Emits SchedulerHeartbeat event via AIOrchestrator
 * - AISessionScheduler checks slot status and decides what to execute
 * - If slot occupied: watchdog check for inactive sessions
 * - If queue not empty: process queue first
 * - If queue empty: ask AutomationScheduler for next scheduled automation
 */
class SchedulerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        LogManager.service("SchedulerWorker tick cycle started", "DEBUG")

        return try {
            // Delegate to AIOrchestrator.tick()
            // tick() will:
            // 1. Check if slot occupied (early return if yes)
            // 2. Process queue if not empty
            // 3. Ask AutomationScheduler for next scheduled automation if queue empty
            AIOrchestrator.tick()

            LogManager.service("SchedulerWorker tick cycle completed", "DEBUG")
            Result.success()

        } catch (e: Exception) {
            LogManager.service("SchedulerWorker tick cycle failed: ${e.message}", "ERROR", e)
            Result.retry()
        }
    }
}
