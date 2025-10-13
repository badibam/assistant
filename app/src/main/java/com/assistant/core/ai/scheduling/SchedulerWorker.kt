package com.assistant.core.ai.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.assistant.core.ai.orchestration.AIOrchestrator
import com.assistant.core.coordinator.Coordinator
import com.assistant.core.coordinator.isSuccess
import com.assistant.core.utils.LogManager

/**
 * WorkManager worker for scheduling automation executions
 *
 * Runs periodically (every 15 minutes) to check which automations should trigger
 * and delegates execution to AIOrchestrator
 */
class SchedulerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val coordinator = Coordinator(applicationContext)

    override suspend fun doWork(): Result {
        LogManager.service("SchedulerWorker started - checking pending automations", "INFO")

        return try {
            // 1. Get all enabled automations
            val result = coordinator.processUserAction("automations.list_all", emptyMap())

            if (!result.isSuccess) {
                LogManager.service("Failed to load automations: ${result.error}", "ERROR")
                return Result.retry()
            }

            val automations = result.data?.get("automations") as? List<*> ?: emptyList<Any>()
            val now = System.currentTimeMillis()

            // 2. Filter enabled automations with schedule that should trigger
            val pendingAutomations = automations.mapNotNull { automationData ->
                val automation = automationData as? Map<*, *> ?: return@mapNotNull null
                val isEnabled = automation["is_enabled"] as? Boolean ?: false
                val scheduleJson = automation["schedule"] as? String

                if (!isEnabled || scheduleJson.isNullOrEmpty() || scheduleJson == "null") {
                    return@mapNotNull null // Skip disabled or manual-only automations
                }

                // Parse schedule to get nextExecutionTime
                val schedule = try {
                    kotlinx.serialization.json.Json.decodeFromString<com.assistant.core.utils.ScheduleConfig>(scheduleJson)
                } catch (e: Exception) {
                    LogManager.service("Failed to parse schedule for automation ${automation["id"]}: ${e.message}", "ERROR")
                    return@mapNotNull null
                }

                val nextExecution = schedule.nextExecutionTime
                if (nextExecution != null && nextExecution <= now) {
                    // Should trigger
                    automation to nextExecution
                } else {
                    null
                }
            }.sortedBy { it.second } // Sort by scheduledExecutionTime (FIFO by scheduled time)

            if (pendingAutomations.isEmpty()) {
                LogManager.service("No pending automations to execute", "DEBUG")
                return Result.success()
            }

            LogManager.service("Found ${pendingAutomations.size} pending automations", "INFO")

            // 3. Trigger each automation (AIOrchestrator handles queueing)
            for ((automation, scheduledTime) in pendingAutomations) {
                val automationId = automation["id"] as? String ?: continue
                val automationName = automation["name"] as? String ?: "Unknown"

                LogManager.service("Triggering automation: $automationName ($automationId) scheduled for $scheduledTime", "INFO")

                try {
                    AIOrchestrator.executeAutomation(automationId)

                    // Update nextExecutionTime for this automation
                    // Note: This will be calculated in AutomationService when updating
                    // For now, we just trigger. The service will handle recalculation on next update.

                } catch (e: Exception) {
                    LogManager.service("Failed to execute automation $automationId: ${e.message}", "ERROR", e)
                }
            }

            LogManager.service("SchedulerWorker completed successfully", "INFO")
            Result.success()

        } catch (e: Exception) {
            LogManager.service("SchedulerWorker failed: ${e.message}", "ERROR", e)
            Result.retry()
        }
    }
}
